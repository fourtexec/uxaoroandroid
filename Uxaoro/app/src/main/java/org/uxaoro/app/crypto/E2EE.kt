package org.uxaoro.app.crypto

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Hybrid E2EE: AES-256-GCM for the payload, RSA-OAEP (SHA-256) wrapping of the AES key
 * for every chat participant (including the sender). The server only stores ciphertext.
 */
object E2EE {
    data class Encrypted(
        val ciphertext: String,
        val nonce: String,
        val keysJson: String,
    )

    /** Text + optional file encrypted under ONE message key (file gets its own nonce). */
    data class EncryptedBundle(
        val ciphertext: String,
        val nonce: String,
        val keysJson: String,
        val mediaNonce: String,
        val mediaCiphertext: String,
    )

    data class PreparedRotation(
        val privatePem: String,
        val publicPem: String,
        val encrypted: Encrypted,
    )

    private val oaep = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA256,
        PSource.PSpecified.DEFAULT,
    )
    private val rng = SecureRandom()

    fun ensureKeys(ctx: Context): KeyPair {
        val privF = File(ctx.filesDir, "e2ee_private.pem")
        val pubF = File(ctx.filesDir, "e2ee_public.pem")
        if (privF.exists() && pubF.exists()) {
            val priv = decodePrivate(privF.readText())
            val pub = decodePublic(pubF.readText())
            return KeyPair(pub, priv)
        }
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048, rng)
        val kp = gen.generateKeyPair()
        privF.writeText(encodePrivate(kp.private))
        pubF.writeText(encodePublic(kp.public))
        return kp
    }

    fun publicPem(ctx: Context): String {
        val f = File(ctx.filesDir, "e2ee_public.pem")
        if (f.exists()) return f.readText()
        return encodePublic(ensureKeys(ctx).public)
    }

    fun exportPrivate(ctx: Context): String {
        ensureKeys(ctx)
        return File(ctx.filesDir, "e2ee_private.pem").readText()
    }

    fun importPrivate(ctx: Context, pem: String) {
        val priv = decodePrivate(pem)
        val pub = publicFromPrivate(priv)
        File(ctx.filesDir, "e2ee_private.pem").writeText(encodePrivate(priv))
        File(ctx.filesDir, "e2ee_public.pem").writeText(encodePublic(pub))
    }

    fun prepareKeyRotation(ctx: Context, userId: String, rotationId: String): PreparedRotation {
        val oldPublicPem = publicPem(ctx)
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048, rng)
        val replacement = gen.generateKeyPair()
        val privatePem = encodePrivate(replacement.private)
        val publicPem = encodePublic(replacement.public)
        val bundle = JSONObject()
            .put("version", 1)
            .put("rotation_id", rotationId)
            .put("user_id", userId)
            .put("private_pem", privatePem)
            .put("public_pem", publicPem)
            .toString()
        return PreparedRotation(privatePem, publicPem, encrypt(bundle, mapOf(userId to oldPublicPem)))
    }

    fun importKeyRotation(
        ctx: Context,
        userId: String,
        rotationId: String,
        newPublicPem: String,
        ciphertext: String,
        nonce: String,
        keysJson: String,
    ) {
        val plain = decrypt(ctx, ciphertext, nonce, keysJson, userId)
            ?: throw IllegalStateException("Could not decrypt the key-rotation bundle")
        val bundle = JSONObject(plain)
        if (bundle.optString("user_id") != userId || bundle.optString("rotation_id") != rotationId) {
            throw IllegalStateException("Key-rotation bundle belongs to another account or reset")
        }
        val privatePem = bundle.optString("private_pem")
        val bundledPublic = decodePublic(bundle.optString("public_pem"))
        val expectedPublic = decodePublic(newPublicPem)
        val derivedPublic = publicFromPrivate(decodePrivate(privatePem))
        if (!bundledPublic.encoded.contentEquals(expectedPublic.encoded) ||
            !derivedPublic.encoded.contentEquals(expectedPublic.encoded)
        ) {
            throw IllegalStateException("Key-rotation bundle does not match the published public key")
        }
        importPrivate(ctx, privatePem)
    }

    // ---- Server-side key backup (password-derived, AES-256-GCM) ----------------
    //
    // The private key is sealed with a key derived from the account password so a
    // new device can restore it after an ordinary sign-in instead of a device
    // pairing ceremony. The password never leaves the phone; the server stores
    // only the envelope below. The wire format is byte-identical to the web
    // client's (backend/web/crypto.js) — see the interop test:
    //
    //   {version:1, kdf:"PBKDF2-HMAC-SHA256", iterations:600000,
    //    salt:b64(16), nonce:b64(12), ciphertext:b64(GCM),
    //    pubkey_fingerprint:hex(sha256(public_pem))[0:32]}
    //   key  = PBKDF2-HMAC-SHA256(password_utf8, salt, iterations, 32 bytes)
    //   aad  = "uxaoro-e2ee-backup:v1:<user_id>"   <- binds the blob to the account
    //   plain = JSON {private_pem, public_pem, user_id, created_at}

    const val BACKUP_VERSION = 1
    const val BACKUP_KDF = "PBKDF2-HMAC-SHA256"
    const val BACKUP_ITERATIONS = 600_000
    private const val BACKUP_SALT_BYTES = 16

    /** What the client uploads and downloads: the server stores it verbatim. */
    data class KeyBackup(
        val version: Int,
        val kdf: String,
        val iterations: Int,
        val salt: String,
        val nonce: String,
        val ciphertext: String,
        val pubkeyFingerprint: String,
        val updatedAt: String? = null,
    )

    private fun backupAad(userId: String) = "uxaoro-e2ee-backup:v$BACKUP_VERSION:$userId"

    private fun deriveBackupKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /** SHA-256 over the public PEM, first 32 hex chars. Lets a client notice
     *  that the account has since generated a different key. */
    fun pubkeyFingerprint(publicPem: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(publicPem.trim().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun sealBackup(
        ctx: Context,
        password: String,
        userId: String,
        iterations: Int = BACKUP_ITERATIONS,
        createdAt: String = java.time.Instant.now().toString(),
    ): KeyBackup {
        require(password.isNotBlank()) { "A password is required to back up your keys" }
        require(userId.isNotBlank()) { "Missing account id" }
        val privatePem = exportPrivate(ctx).trim()
        val publicPem = publicPem(ctx).trim()
        val salt = ByteArray(BACKUP_SALT_BYTES).also { rng.nextBytes(it) }
        val nonce = ByteArray(12).also { rng.nextBytes(it) }
        val key = deriveBackupKey(password, salt, iterations)
        val plain = JSONObject()
            .put("private_pem", privatePem)
            .put("public_pem", publicPem)
            .put("user_id", userId)
            .put("created_at", createdAt)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        c.updateAAD(backupAad(userId).toByteArray(Charsets.UTF_8))
        val ct = c.doFinal(plain)
        return KeyBackup(
            version = BACKUP_VERSION,
            kdf = BACKUP_KDF,
            iterations = iterations,
            salt = b64(salt),
            nonce = b64(nonce),
            ciphertext = b64(ct),
            pubkeyFingerprint = pubkeyFingerprint(publicPem),
        )
    }

    /** Open a stored envelope and return the PEMs inside it.
     *  Throws [IllegalStateException] with a user-facing message on failure. */
    fun openBackup(password: String, userId: String, backup: KeyBackup): Pair<String, String> {
        if (password.isBlank()) throw IllegalStateException("Enter your account password")
        if (backup.ciphertext.isBlank()) throw IllegalStateException("This account has no key backup")
        val key = deriveBackupKey(password, unb64(backup.salt), backup.iterations)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(backup.nonce)))
        c.updateAAD(backupAad(userId).toByteArray(Charsets.UTF_8))
        val plain = try {
            String(c.doFinal(unb64(backup.ciphertext)), Charsets.UTF_8)
        } catch (_: Exception) {
            // GCM tag mismatch: wrong password, or the blob was tampered with.
            throw IllegalStateException("Could not decrypt the backup — wrong password?")
        }
        val obj = JSONObject(plain)
        val owner = obj.optString("user_id")
        if (owner.isNotBlank() && owner != userId) {
            throw IllegalStateException("That backup belongs to another account")
        }
        val privatePem = obj.optString("private_pem")
        if (privatePem.isBlank()) throw IllegalStateException("The backup is missing the private key")
        return privatePem to obj.optString("public_pem")
    }

    /** Restore a backup into this device's key files. */
    fun restoreBackup(ctx: Context, password: String, userId: String, backup: KeyBackup): Boolean {
        val (privatePem, _) = openBackup(password, userId, backup)
        importPrivate(ctx, privatePem)
        return true
    }

    /** True when this device already holds a private key on disk. */
    fun hasLocalKeys(ctx: Context): Boolean = File(ctx.filesDir, "e2ee_private.pem").exists()

    fun encrypt(plain: String, pubkeysPem: Map<String, String>): Encrypted {
        val b = encryptBundle(plain, pubkeysPem, null)
        return Encrypted(b.ciphertext, b.nonce, b.keysJson)
    }

    /** Encrypts `plain` (text/payload) and `fileBytes` (E2EE chat media) with a
     *  single AES-256-GCM message key; the key is RSA-OAEP wrapped per recipient.
     *  The file uses its own random nonce — the (key, nonce) pair is never reused. */
    fun encryptBundle(plain: String, pubkeysPem: Map<String, String>, fileBytes: ByteArray?): EncryptedBundle {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256, rng)
        val aes = kg.generateKey()
        val keys = JSONObject()
        for ((uid, pem) in pubkeysPem) {
            if (pem.isBlank()) continue
            try {
                val wrapped = rsaWrap(decodePublic(pem), aes.encoded)
                keys.put(uid, b64(wrapped))
            } catch (_: Exception) {
            }
        }
        val nonce = ByteArray(12).also { rng.nextBytes(it) }
        val ct = aesEncrypt(aes, nonce, plain.toByteArray(Charsets.UTF_8))
        var mediaNonce = ""
        var mediaCt = ""
        if (fileBytes != null && fileBytes.isNotEmpty()) {
            val mn = ByteArray(12).also { rng.nextBytes(it) }
            mediaCt = b64(aesEncrypt(aes, mn, fileBytes))
            mediaNonce = b64(mn)
        }
        return EncryptedBundle(b64(ct), b64(nonce), keys.toString(), mediaNonce, mediaCt)
    }

    fun unwrapKey(ctx: Context, keysJson: String, myUserId: String): SecretKey? = try {
        val keys = JSONObject(keysJson.ifBlank { "{}" })
        if (!keys.has(myUserId)) null
        else {
            val raw = rsaUnwrap(ensureKeys(ctx).private, unb64(keys.getString(myUserId)))
            SecretKeySpec(raw, "AES")
        }
    } catch (_: Exception) {
        null
    }

    fun decryptBytes(ctx: Context, ciphertext: ByteArray, nonce: String, keysJson: String, myUserId: String): ByteArray? {
        return try {
            val key = unwrapKey(ctx, keysJson, myUserId) ?: return null
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(nonce)))
            c.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    private fun aesEncrypt(key: SecretKey, nonce: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return c.doFinal(data)
    }

    fun decrypt(ctx: Context, ciphertext: String, nonce: String, keysJson: String, myUserId: String): String? {
        return try {
            val keys = JSONObject(keysJson.ifBlank { "{}" })
            if (!keys.has(myUserId)) return null
            val wrapped = unb64(keys.getString(myUserId))
            val aesRaw = rsaUnwrap(ensureKeys(ctx).private, wrapped)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aesRaw, "AES"),
                GCMParameterSpec(128, unb64(nonce)),
            )
            String(c.doFinal(unb64(ciphertext)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun rsaWrap(pub: PublicKey, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("RSA/ECB/OAEPPadding")
        c.init(Cipher.ENCRYPT_MODE, pub, oaep)
        return c.doFinal(data)
    }

    private fun rsaUnwrap(priv: PrivateKey, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("RSA/ECB/OAEPPadding")
        c.init(Cipher.DECRYPT_MODE, priv, oaep)
        return c.doFinal(data)
    }

    private fun encodePublic(key: PublicKey): String {
        val b = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n${chunk(b)}\n-----END PUBLIC KEY-----"
    }

    private fun encodePrivate(key: PrivateKey): String {
        val b = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        return "-----BEGIN PRIVATE KEY-----\n${chunk(b)}\n-----END PRIVATE KEY-----"
    }

    private fun decodePublic(pem: String): PublicKey {
        val raw = pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val spec = X509EncodedKeySpec(Base64.decode(raw, Base64.DEFAULT))
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }

    private fun decodePrivate(pem: String): PrivateKey {
        val raw = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val spec = PKCS8EncodedKeySpec(Base64.decode(raw, Base64.DEFAULT))
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }

    private fun publicFromPrivate(priv: PrivateKey): PublicKey {
        // Re-derive by encoding isn't possible from PKCS8 RSA without CRT params via KeyFactory.
        // Generate public from RSAPrivateCrtKey if available.
        val crt = priv as java.security.interfaces.RSAPrivateCrtKey
        val spec = java.security.spec.RSAPublicKeySpec(crt.modulus, crt.publicExponent)
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }

    private fun chunk(s: String) = s.chunked(64).joinToString("\n")
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.DEFAULT)
}
