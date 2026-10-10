package org.uxaoro.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.Prefs
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Uxaoro app-only proxy manager for Android.
 *
 * Applies the user-configured HTTP or SOCKS5 proxy strictly to Uxaoro's own
 * OkHttpClient instances (API, WebSocket, Coil media, and News) without
 * modifying Android's system/global proxy settings.
 *
 * Also supports the "Don't use proxy when VPN is on" setting
 * ([Prefs.proxyBypassOnVpn]): when enabled and an active VPN transport is
 * detected on the device, connections automatically bypass the proxy and go
 * directly over the VPN tunnel.
 */
object AppProxy {

    @Volatile
    private var appContext: Context? = null

    private val _vpnActive = MutableStateFlow(false)
    val vpnActive: StateFlow<Boolean> = _vpnActive.asStateFlow()

    fun attach(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        _vpnActive.value = isVpnActive(ctx)
        syncAuthenticator()
        registerVpnMonitor(ctx)
    }

    /**
     * Checks whether a VPN connection is currently active on the device using
     * both ConnectivityManager (TRANSPORT_VPN) and active network interface
     * names (tun/ppp/wg/tap/utun/ipsec) as a fallback.
     */
    fun isVpnActive(ctx: Context? = appContext): Boolean {
        if (ctx != null) {
            runCatching {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    val activeCaps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                    if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                        return true
                    }
                    @Suppress("DEPRECATION")
                    for (net in cm.allNetworks) {
                        val caps = cm.getNetworkCapabilities(net)
                        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                            return true
                        }
                    }
                }
            }
        }
        return runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching false
            while (ifaces.hasMoreElements()) {
                val nif = ifaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase()
                if (
                    name.startsWith("tun") ||
                    name.startsWith("ppp") ||
                    name.startsWith("pptp") ||
                    name.startsWith("wg") ||
                    name.startsWith("tap") ||
                    name.startsWith("utun") ||
                    name.startsWith("ipsec")
                ) {
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    private fun registerVpnMonitor(ctx: Context) {
        runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val req = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            cm.registerNetworkCallback(
                req,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = refreshVpnState()
                    override fun onLost(network: Network) = refreshVpnState()
                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) = refreshVpnState()
                },
            )
        }
    }

    fun refreshVpnState() {
        val prev = _vpnActive.value
        val now = isVpnActive()
        _vpnActive.value = now
        if (prev != now && runCatching { Prefs.proxyEnabled && Prefs.proxyBypassOnVpn }.getOrDefault(false)) {
            runCatching { Api.http?.connectionPool?.evictAll() }
        }
    }

    /** True when the proxy is enabled, configured, and not currently bypassed by VPN. */
    fun shouldUseProxy(): Boolean {
        val enabled = runCatching { Prefs.proxyEnabled }.getOrDefault(false)
        if (!enabled) return false
        val host = runCatching { Prefs.proxyHost.trim() }.getOrDefault("")
        val port = runCatching { Prefs.proxyPort }.getOrDefault(0)
        if (host.isBlank() || port !in 1..65535) return false
        val bypassVpn = runCatching { Prefs.proxyBypassOnVpn }.getOrDefault(false)
        if (bypassVpn && isVpnActive()) return false
        return true
    }

    /**
     * Dynamic ProxySelector scoped to Uxaoro's OkHttpClient instances.
     * Evaluates VPN state on each new connection so "Don't use proxy when VPN is on"
     * takes effect immediately without restarting the app.
     */
    private val selector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            refreshVpnState()
            if (!shouldUseProxy()) return listOf(Proxy.NO_PROXY)
            val host = runCatching { Prefs.proxyHost.trim() }.getOrDefault("")
            val port = runCatching { Prefs.proxyPort }.getOrDefault(0)
            if (host.isBlank() || port !in 1..65535) return listOf(Proxy.NO_PROXY)
            val typeStr = runCatching { Prefs.proxyType.uppercase() }.getOrDefault("HTTP")
            val type = if (typeStr.startsWith("SOCKS")) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val addr = runCatching { InetSocketAddress(host, port) }
                .getOrElse { InetSocketAddress.createUnresolved(host, port) }
            return listOf(Proxy(type, addr))
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            // Let OkHttp surface the connection failure directly to the caller.
        }
    }

    private val httpProxyAuthenticator = Authenticator { _, response ->
        if (!shouldUseProxy()) return@Authenticator null
        val user = runCatching { Prefs.proxyUsername }.getOrDefault("")
        val pass = runCatching { Prefs.proxyPassword }.getOrDefault("")
        if (user.isBlank()) return@Authenticator null
        if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
        val credential = Credentials.basic(user, pass)
        response.request.newBuilder()
            .header("Proxy-Authorization", credential)
            .build()
    }

    /**
     * Installs process-scoped SOCKS/proxy credentials when configured.
     * Because Android runs Uxaoro in its own isolated application process,
     * this affects only Uxaoro.
     */
    fun syncAuthenticator() {
        runCatching {
            val enabled = Prefs.proxyEnabled
            val user = Prefs.proxyUsername
            val pass = Prefs.proxyPassword
            if (enabled && user.isNotBlank()) {
                java.net.Authenticator.setDefault(
                    object : java.net.Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication? {
                            if (!shouldUseProxy()) return null
                            return PasswordAuthentication(user, pass.toCharArray())
                        }
                    },
                )
            } else {
                java.net.Authenticator.setDefault(null)
            }
        }
    }

    /**
     * Attaches the Uxaoro-only proxy selector and authenticator to [builder].
     */
    fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        syncAuthenticator()
        return builder
            .proxySelector(selector)
            .proxyAuthenticator(httpProxyAuthenticator)
    }

    /**
     * Human-readable status summary for the Settings UI.
     */
    fun statusSummary(vpnNow: Boolean = isVpnActive()): String {
        val enabled = runCatching { Prefs.proxyEnabled }.getOrDefault(false)
        if (!enabled) return "Proxy is off (Uxaoro connects directly)."
        val host = runCatching { Prefs.proxyHost.trim() }.getOrDefault("")
        val port = runCatching { Prefs.proxyPort }.getOrDefault(0)
        if (host.isBlank() || port !in 1..65535) {
            return "Proxy is on — enter a valid host and port below."
        }
        val bypassVpn = runCatching { Prefs.proxyBypassOnVpn }.getOrDefault(false)
        if (bypassVpn && vpnNow) {
            return "VPN is active — proxy bypassed (\"Don't use proxy when VPN is on\")."
        }
        val type = runCatching { Prefs.proxyType.uppercase() }.getOrDefault("HTTP")
        val auth = if (runCatching { Prefs.proxyUsername.isNotBlank() }.getOrDefault(false)) " (with auth)" else ""
        return "Active for Uxaoro only: $type $host:$port$auth"
    }
}
