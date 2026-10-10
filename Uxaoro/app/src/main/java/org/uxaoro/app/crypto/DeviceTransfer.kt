package org.uxaoro.app.crypto

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secure cross-device transfer of the E2EE identity private key.
 *
 * Protocol overview
 * -----------------
 *  1. NEW device generates an ephemeral EC P-256 keypair and POSTs the public
 *     key to the server along with a label/platform.
 *  2. EXISTING device (already logged in, has the private key) gets a
 *     WebSocket push, generates its own ephemeral P-256 keypair, performs ECDH
 *     with the new device's public key, and derives a shared AES-256 key via
 *     HKDF-SHA256(salt = pair_code, info = "uxaoro/device-transfer/v1").
 *  3. BOTH sides display a 6-digit Short Authentication String (SAS) derived
 *     from HMAC-SHA256(shared_secret, "sas") truncated to 6 digits. If codes
 *     match on both screens, no MITM is present (the ephemeral keys were not
 *     tampered with in transit).
 *  4. Existing device encrypts the private-key PEM bundle (plus public-key PEM
 *     for sanity) with AES-256-GCM keyed by the HKDF output and uploads.
 *  5. New device fetches the encrypted blob (or receives it via WS), decrypts,
 *     imports the key, and calls /complete.
 *
 * The server never sees plaintext private keys.
 */
object DeviceTransfer {

    data class EphemeralKeys(
        val keyPair: KeyPair,
        val publicKeyPem: String,
    )

    data class Bundle(
        val privateKeyPem: String,
        val publicKeyPem: String,
    )

    data class Sealed(
        val ciphertextB64: String,
        val nonceB64: String,
        val sasCode: String,  // 6-digit numeric short auth string
    )

    data class UnsealResult(
        val bundle: Bundle,
        val sasCode: String,
    )

    private const val EC_CURVE = "secp256r1"  // P-256 — universally available on Android
    private const val HKDF_INFO = "uxaoro/device-transfer/v1"
    private const val AES_GCM_TAG_BITS = 128
    private const val NONCE_LEN = 12
    private val rng = SecureRandom()

    // ---------- Ephemeral EC keys ----------

    fun generateEphemeral(): EphemeralKeys {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec(EC_CURVE), rng)
        val kp = gen.generateKeyPair()
        return EphemeralKeys(kp, encodeEcPublic(kp.public))
    }

    // ---------- Sender (existing device): seal bundle ----------

    fun sealBundle(
        bundle: Bundle,
        ourEphemeralKp: KeyPair,
        theirEphemeralPubPem: String,
        pairingCode: String,
    ): Sealed {
        val theirPub = decodeEcPublic(theirEphemeralPubPem)
        val shared = ecdh(ourEphemeralKp.private, theirPub)
        val aes = hkdfSha256(shared, pairingCode.toByteArray(StandardCharsets.UTF_8), HKDF_INFO.toByteArray(StandardCharsets.UTF_8))
        val sas = deriveSas(shared)
        val nonce = ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        val plain = JSONObject().apply {
            put("private_pem", bundle.privateKeyPem)
            put("public_pem", bundle.publicKeyPem)
            put("v", 1)
        }.toString().toByteArray(StandardCharsets.UTF_8)
        val ct = aesGcmEncrypt(SecretKeySpec(aes, "AES"), nonce, plain)
        return Sealed(
            ciphertextB64 = b64(ct),
            nonceB64 = b64(nonce),
            sasCode = sas,
        )
    }

    // ---------- Receiver (new device): unseal bundle ----------

    fun unsealBundle(
        ourEphemeralKp: KeyPair,
        theirEphemeralPubPem: String,
        pairingCode: String,
        ciphertextB64: String,
        nonceB64: String,
    ): UnsealResult {
        val theirPub = decodeEcPublic(theirEphemeralPubPem)
        val shared = ecdh(ourEphemeralKp.private, theirPub)
        val aes = hkdfSha256(shared, pairingCode.toByteArray(StandardCharsets.UTF_8), HKDF_INFO.toByteArray(StandardCharsets.UTF_8))
        val sas = deriveSas(shared)
        val pt = aesGcmDecrypt(
            SecretKeySpec(aes, "AES"),
            unb64(nonceB64),
            unb64(ciphertextB64),
        )
        val json = JSONObject(String(pt, StandardCharsets.UTF_8))
        return UnsealResult(
            bundle = Bundle(
                privateKeyPem = json.getString("private_pem"),
                publicKeyPem = json.getString("public_pem"),
            ),
            sasCode = sas,
        )
    }

    /** SAS for user verification — 6 decimal digits. Derived from the ECDH
     *  shared secret alone, so both sides can compute it before decrypting. */
    fun sasFromPublicKeys(ourKp: KeyPair, theirPubPem: String): String {
        val theirPub = decodeEcPublic(theirPubPem)
        val shared = ecdh(ourKp.private, theirPub)
        return deriveSas(shared)
    }

    private fun deriveSas(sharedSecret: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sharedSecret, "HmacSHA256"))
        val out = mac.doFinal("sas".toByteArray(StandardCharsets.UTF_8))
        // Take first 4 bytes -> unsigned 32-bit integer; format as 6 digits (mod 10^6)
        var n = 0L
        for (i in 0 until 4) {
            n = (n shl 8) or (out[i].toInt() and 0xFF).toLong()
        }
        val code = (n % 1_000_000L).toInt()
        return code.toString().padStart(6, '0')
    }

    // ---------- Crypto primitives ----------

    private fun ecdh(priv: PrivateKey, pub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    /** HKDF-SHA256 (RFC 5869) — extract-then-expand, L=32 bytes (AES-256). */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int = 32): ByteArray {
        val prk = run {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
            mac.doFinal(ikm)
        }
        // expand
        val n = (len + 32 - 1) / 32
        var t = byteArrayOf()
        val okm = java.io.ByteArrayOutputStream()
        for (i in 1..n) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            okm.write(t)
        }
        return okm.toByteArray().copyOf(len)
    }

    private fun aesGcmEncrypt(key: SecretKey, nonce: ByteArray, plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(AES_GCM_TAG_BITS, nonce))
        return c.doFinal(plain)
    }

    private fun aesGcmDecrypt(key: SecretKey, nonce: ByteArray, ct: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AES_GCM_TAG_BITS, nonce))
        return c.doFinal(ct)
    }

    // ---------- EC PEM encode/decode ----------

    private fun encodeEcPublic(pub: PublicKey): String {
        val b = Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n${b.chunked(64).joinToString("\n")}\n-----END PUBLIC KEY-----"
    }

    private fun decodeEcPublic(pem: String): PublicKey {
        val raw = pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(bytes)
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.DEFAULT)
}
