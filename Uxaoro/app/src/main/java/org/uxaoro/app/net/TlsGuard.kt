package org.uxaoro.app.net

import android.net.http.X509TrustManagerExtensions
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.uxaoro.app.data.Prefs
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Uxaoro TLS guard — resets connections that go through an SSL-proxying proxy.
 *
 * Uxaoro keeps private messages, media and calls end-to-end encrypted, and TLS
 * is the only thing that keeps a middle box from seeing (and tampering with)
 * the traffic in the first place. An "SSL proxying" proxy — mitmproxy, Charles,
 * Fiddler, Burp, Proxyman, an MDM/AV inspection appliance — terminates TLS on
 * the device, re-encrypts it upstream and therefore sits inside the channel.
 *
 * This guard makes that impossible on Android:
 *
 *  1. [GuardedTrustManager] runs the normal platform PKI validation first
 *     (chain, signatures, expiry, revocation) and then refuses certificates
 *     that can only come from an intercepting proxy. Throwing there aborts the
 *     handshake, so the socket is torn down before a single byte of the request
 *     is written — a reset, never a silent downgrade.
 *  2. [handshakeGuard] re-checks every finished handshake with full context
 *     (URL + configured proxy, pooled/externally created connections included)
 *     and cancels the exchange before the request is written.
 *  3. [status] / [blocked] tell the UI what was reset and why.
 *
 * Detection signals (any single one is enough):
 *  - Android knows the CA as user-installed ([X509TrustManagerExtensions.isUserAddedCertificate]);
 *    that is exactly what an interception proxy installs on the device;
 *  - a well known interception CA name in the subject/issuer of the chain;
 *  - a chain that is anchored in a user-installed CA (AndroidCAStore "user:");
 *  - strict mode only: a two-certificate chain ("proxy leaf" -> "proxy root")
 *    seen over a proxy, for appliances whose root is trusted system-wide.
 *
 * Installed from [org.uxaoro.app.data.Api.rebuild] and for every other HTTPS
 * consumer (Coil media downloads) via [harden]. Controlled by
 * [Prefs.tlsGuard] / [Prefs.tlsGuardStrict].
 */
object TlsGuard {

    /** Marker embedded in every error the guard raises, so callers can spot us. */
    const val MARK = "UXAORO_TLS_GUARD"
    private const val TAG = "UxaoroTlsGuard"

    /**
     * Names that only ever appear in a certificate chain when something is
     * intercepting TLS. Matched case-insensitively against every subject and
     * issuer DN in the chain.
     */
    private val INTERCEPTORS = listOf(
        "mitmproxy", "charles", "fiddler", "portswigger", "burp", "owasp zap",
        "proxyman", "httptoolkit", "http toolkit", "pulpcut", "sslsplit",
        "polarproxy", "mitm", "do_not_trust", "do not trust",
        "zscaler", "netskope", "bluecoat", "blue coat", "websense", "forcepoint",
        "palo alto", "fortinet", "watchguard", "sophos", "smoothwall",
        "kaspersky", "bitdefender", "eset inspection", "avast", "mcafee web gateway",
    )

    /** A connection the guard reset. */
    data class Blocked(
        val host: String,
        val reason: String,
        val via: String,
        val at: Long = System.currentTimeMillis(),
    )

    private val _status = MutableStateFlow<Blocked?>(null)

    /** Last reset connection, or null. Drives the in-app warning. */
    val status: StateFlow<Blocked?> = _status.asStateFlow()

    private val _blocked = MutableStateFlow(0)

    /** How many connections have been reset since the process started. */
    val blocked: StateFlow<Int> = _blocked.asStateFlow()

    fun clearStatus() {
        _status.value = null
    }

    /** Guard switched on? On by default; must never crash before [Prefs.init]. */
    fun enabled(): Boolean = runCatching { Prefs.tlsGuard }.getOrDefault(true)

    /** Aggressive mode: also reset two-certificate chains seen over a proxy. */
    fun strict(): Boolean = runCatching { Prefs.tlsGuardStrict }.getOrDefault(false)

    private fun record(host: String, reason: String, via: String): Blocked {
        val b = Blocked(host.ifBlank { "?" }, reason, via)
        _status.value = b
        _blocked.value += 1
        Log.w(TAG, "reset connection to ${b.host}: $reason (via ${b.via})")
        return b
    }

    @Volatile private var platformTm: X509TrustManager? = null

    /* ---------------- certificate inspection ---------------- */

    /**
     * Returns a human readable reason when [chain] proves that the TLS session
     * is intercepted, or null when the chain looks like a genuine Uxaoro
     * server. [chain] is ordered leaf -> root, as in the TLS handshake.
     */
    fun inspect(chain: Array<out X509Certificate>): String? {
        if (chain.isEmpty()) return null
        // (1) Known interception products always name themselves in the DN.
        for (c in chain) {
            val dn = (c.subjectX500Principal.name + " || " + c.issuerX500Principal.name).lowercase()
            INTERCEPTORS.firstOrNull { dn.contains(it) }?.let { return "certificate signed by $it" }
        }
        // (2) A CA Android recorded as user-installed is the proxy's anchor.
        addedCertChecker?.let { ext ->
            for (c in chain) {
                if (runCatching { ext.isUserAddedCertificate(c) }.getOrDefault(false)) {
                    return "user-installed interception CA in the chain"
                }
            }
        }
        // (3) Fallback for devices where (2) is unavailable: AndroidCAStore.
        val userCas = userCaSubjects()
        if (userCas.isNotEmpty()) {
            for (c in chain) {
                if (c.issuerX500Principal in userCas || c.subjectX500Principal in userCas) {
                    return "chain anchored in a user-installed CA"
                }
            }
        }
        return null
    }

    /** First RDN of the subject, for log lines and status text. */
    private fun shortName(c: X509Certificate): String =
        c.subjectX500Principal.name.split(',').firstOrNull()?.trim().orEmpty().take(80)

    /**
     * Android's own "is this CA user added" helper (API 24+). Returns null when
     * the platform trust manager cannot be wrapped (e.g. plain JVM tests).
     */
    private val addedCertChecker: X509TrustManagerExtensions? by lazy {
        runCatching { X509TrustManagerExtensions(platformTrustManager()) }.getOrNull()
    }

    /** Subjects of the CAs the device owner (not the OEM) installed. */
    private val userCaSubjectsCache: Set<javax.security.auth.x500.X500Principal> by lazy {
        runCatching {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null, null)
            ks.aliases().toList()
                .filter { it.startsWith("user:") }
                .mapNotNull { (ks.getCertificate(it) as? X509Certificate)?.subjectX500Principal }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun userCaSubjects(): Set<javax.security.auth.x500.X500Principal> = userCaSubjectsCache

    /** The stock Android trust manager (system CAs only). */
    fun platformTrustManager(): X509TrustManager {
        platformTm?.let { return it }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val tm = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        platformTm = tm
        return tm
    }

    /**
     * Platform PKI validation first, interception detection second. Throwing
     * here aborts the handshake, so the socket is closed before the request
     * (token, message, key material) is ever written: the connection is reset.
     */
    private class GuardedTrustManager(private val delegate: X509TrustManager) : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            delegate.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            delegate.checkServerTrusted(chain, authType)
            if (chain == null || chain.isEmpty() || !enabled()) return
            val reason = inspect(chain) ?: return
            record(shortName(chain.last()), reason, "tls handshake")
            throw CertificateException("$MARK $reason")
        }
    }

    /* ---------------- connection reset ---------------- */

    /**
     * Second line of defence: runs after the handshake is up but before the
     * request is written, so it also covers pooled or externally created
     * connections — and it knows the URL and the configured proxy.
     */
    private val handshakeGuard = Interceptor { chain ->
        if (!enabled()) return@Interceptor chain.proceed(chain.request())
        val url = chain.request().url
        val conn = chain.connection()
        val handshake = conn?.handshake()
        if (handshake != null) {
            val certs = handshake.peerCertificates.filterIsInstance<X509Certificate>().toTypedArray()
            val proxy = if (url.isHttps) proxyFor(url) else null
            val reason = inspect(certs) ?: if (proxy != null) proxiedChainReason(certs) else null
            if (reason != null) {
                // Reset it, hard: drop the socket, cancel the exchange and make
                // sure nothing of this connection is ever reused. The request
                // itself is never written, so no token/key/message leaks either.
                runCatching { conn.socket().close() }
                runCatching { chain.call().cancel() }
                val via = if (proxy != null) "proxy ${proxy.type()} ${proxy.address()}" else "tls handshake"
                record(url.host, reason, via)
                throw Reset(url.host, reason)
            }
        }
        chain.proceed(chain.request())
    }

    /**
     * Strict mode only. Inspection appliances whose root is trusted system wide
     * (MDM, rooted device, corporate CA) mint "leaf -> own root" pairs, so a
     * two-certificate chain on a proxied HTTPS session is suspicious. Off by
     * default because a few real sites do sign the leaf with their root.
     */
    private fun proxiedChainReason(chain: Array<out X509Certificate>): String? {
        if (!strict() || chain.size > 2) return null
        val leaf = chain.first()
        val root = chain.last()
        if (root.subjectX500Principal != leaf.issuerX500Principal) return null
        return "two-certificate chain issued by ${shortName(root)} over a proxy"
    }

    /**
     * Installs the guarded TLS stack on [builder]. No-op when the user turned
     * the guard off; toggle through [Prefs.tlsGuard], which rebuilds the client.
     */
    fun harden(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (!enabled()) return builder
        runCatching {
            val tm = GuardedTrustManager(platformTrustManager())
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(tm), SecureRandom())
            builder.sslSocketFactory(ctx.socketFactory as SSLSocketFactory, tm)
        }.onFailure { Log.w(TAG, "cannot install guarded TLS: ${it.message}") }
        return builder.addNetworkInterceptor(handshakeGuard)
    }

    /** Proxy the system (Wi-Fi, VPN or PAC) selects for [url], if any. */
    fun proxyFor(url: HttpUrl): Proxy? {
        val selector = runCatching { ProxySelector.getDefault() }.getOrNull() ?: return null
        return runCatching {
            selector.select(URI(url.toString()))
                .firstOrNull { it.type() != Proxy.Type.DIRECT && it.address() != null }
        }.getOrNull()
    }

    /** Raised when the guard tears a connection down. */
    class Reset(val host: String, val reason: String) : IOException("$MARK $reason")

    /** Reason string when [t] or one of its causes came from the guard. */
    fun resetReason(t: Throwable?): String? {
        var c: Throwable? = t
        var hops = 0
        while (c != null && hops++ < 12) {
            if (c is Reset) return c.reason
            val msg = c.message
            if (msg != null && msg.contains(MARK)) return msg.substringAfter(MARK).trim().trim(':').trim()
            c = c.cause
        }
        return null
    }

    /** User facing text for a guard failure, or null when [t] came from elsewhere. */
    fun userMessage(t: Throwable?): String? = resetReason(t)?.let {
        "Connection reset: TLS is being intercepted by an SSL-proxying proxy ($it). Uxaoro will not send anything through it."
    }
}
