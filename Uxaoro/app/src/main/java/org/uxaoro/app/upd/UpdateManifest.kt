package org.uxaoro.app.upd

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

/**
 * How urgently a published client update has to be installed.
 *
 * The publisher sets it per release in `upd.manifest.json`:
 * `"severity": "normal"` (the user may postpone or skip the version) or
 * `"severity": "required"` (the app shows a blocking, non-dismissible prompt).
 * A normal update is upgraded to [REQUIRED] automatically when the installed
 * client is older than `min_supported_version_code` / `min_supported_version`,
 * or when a `mandatory_from` deadline has passed.
 */
enum class UpdateSeverity {
    NORMAL,
    REQUIRED;

    companion object {
        fun parse(raw: String?): UpdateSeverity = when (raw?.trim()?.lowercase(Locale.ROOT)) {
            "required", "force", "forced", "mandatory", "critical", "security" -> REQUIRED
            else -> NORMAL
        }
    }
}

/**
 * One published Android release, parsed from
 * `https://fourtexec.site/downloads/uxaoro/upd/upd.manifest.json`.
 */
data class AndroidUpdate(
    val version: String,
    val versionCode: Int,
    val severity: UpdateSeverity,
    /** Human readable explanation when [severity] is REQUIRED. */
    val requiredReason: String,
    val notes: String,
    val channel: String,
    val publishedAt: String?,
    val mandatoryFrom: Long?,
    val minSupportedVersion: String?,
    val minSupportedVersionCode: Int?,
    val apkUrl: String,
    val apkSha256: String,
    val apkSize: Long,
    val apkPackage: String,
)

/**
 * Parser for the Uxaoro update manifest. Every field is optional except the
 * release version and the APK url: a missing or malformed field is ignored so a
 * future manifest revision cannot break already-installed clients.
 */
object UpdateManifest {
    const val APP_KEY = "android"
    const val MANIFEST_NAME = "upd.manifest.json"

    /** Default bundle directory of all published builds. */
    const val DEFAULT_BASE = "https://fourtexec.site/downloads/uxaoro/upd/"

    fun manifestUrl(base: String): String {
        val b = if (base.endsWith("/")) base else "$base/"
        return b + MANIFEST_NAME
    }

    /**
     * @param manifestUrl the url the manifest was fetched from — relative
     *   artifact paths are resolved against it when the manifest has no
     *   `base_url` field.
     */
    fun parse(json: String, manifestUrl: String): AndroidUpdate? = try {
        val root = JsonParser.parseString(json).asJsonObject
        parseRoot(root, manifestUrl)
    } catch (_: Exception) {
        null
    }

    private fun parseRoot(root: JsonObject, manifestUrl: String): AndroidUpdate? {
        val entry = root.obj("apps")?.obj(APP_KEY)
            ?: root.obj("clients")?.obj(APP_KEY)
            ?: root.obj(APP_KEY)
            ?: root.takeIf { root.str("version") != null && (root.obj("apk") != null || root.str("url") != null) }
            ?: return null

        val version = entry.str("version", "version_name", "versionName") ?: return null
        val versionCode = entry.num("version_code", "versionCode")?.toInt() ?: 0

        val apk = entry.obj("apk")
        val apkUrlRaw = apk?.str("url", "href", "file") ?: entry.str("apk_url", "url")
        val apkUrl = resolve(apkUrlRaw, baseUrlOf(root, entry, manifestUrl)) ?: return null

        val installedCode = org.uxaoro.app.BuildConfig.VERSION_CODE
        val installedVersion = org.uxaoro.app.BuildConfig.VERSION_NAME

        val minCode = entry.num("min_supported_version_code", "minSupportedVersionCode")?.toInt()
        val minVersion = entry.str("min_supported_version", "minSupportedVersion", "min_version")
        val mandatoryFrom = parseIsoDate(entry.str("mandatory_from", "mandatoryFrom", "required_from"))

        var severity = UpdateSeverity.parse(entry.str("severity", "kind", "type"))
        val requiredFlag = runCatching {
            entry.get("required")?.takeIf { it.isJsonPrimitive }?.asBoolean
        }.getOrNull()
        if (requiredFlag == true) severity = UpdateSeverity.REQUIRED

        val reason = StringBuilder()
        if (severity == UpdateSeverity.NORMAL) {
            if (minCode != null && installedCode < minCode) {
                severity = UpdateSeverity.REQUIRED
                reason.append("the installed version is older than the supported minimum $minCode")
            } else if (minVersion != null && Versions.compare(installedVersion, minVersion) < 0) {
                severity = UpdateSeverity.REQUIRED
                reason.append("the installed version is older than the supported minimum $minVersion")
            } else if (mandatoryFrom != null && System.currentTimeMillis() >= mandatoryFrom) {
                severity = UpdateSeverity.REQUIRED
                reason.append("the mandatory update deadline has passed")
            }
        } else {
            reason.append("the publisher marked this update as required")
        }

        return AndroidUpdate(
            version = version.removePrefix("v").trim(),
            versionCode = versionCode,
            severity = severity,
            requiredReason = reason.toString(),
            notes = notesOf(entry),
            channel = entry.str("channel") ?: root.str("channel") ?: "stable",
            publishedAt = entry.str("published_at", "publishedAt") ?: root.str("generated_at"),
            mandatoryFrom = mandatoryFrom,
            minSupportedVersion = minVersion,
            minSupportedVersionCode = minCode,
            apkUrl = apkUrl,
            apkSha256 = (apk?.str("sha256", "sha", "checksum") ?: entry.str("sha256") ?: "").lowercase(Locale.ROOT),
            apkSize = apk?.num("size", "bytes", "length") ?: entry.num("size") ?: 0L,
            apkPackage = apk?.str("package", "package_name") ?: entry.str("package") ?: "",
        )
    }

    /** The directory all relative artifact paths are resolved against. */
    private fun baseUrlOf(root: JsonObject, entry: JsonObject, manifestUrl: String): String {
        val explicit = root.str("base_url") ?: entry.str("base_url")
        if (!explicit.isNullOrBlank()) {
            return if (explicit.endsWith("/")) explicit else "$explicit/"
        }
        val idx = manifestUrl.lastIndexOf('/')
        return if (idx > 0) manifestUrl.substring(0, idx + 1) else manifestUrl
    }

    private fun resolve(ref: String?, base: String): String? {
        val s = ref?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        return base + s.removePrefix("./").removePrefix("/")
    }

    private fun parseIsoDate(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
    }

    /** Release notes: a plain string, or an object keyed by language. */
    private fun notesOf(entry: JsonObject): String {
        val el = entry.get("notes") ?: entry.get("changelog") ?: entry.get("description")
        return when {
            el == null || el.isJsonNull -> ""
            el.isJsonPrimitive -> el.asString
            el.isJsonObject -> {
                val o = el.asJsonObject
                val lang = Locale.getDefault().language
                o.get(lang)?.takeIf { it.isJsonPrimitive }?.asString
                    ?: o.get("en")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: o.entrySet().firstOrNull { it.value.isJsonPrimitive }?.value?.asString
                    ?: ""
            }
            else -> ""
        }
    }

    /* ---------------- small JsonObject helpers ---------------- */

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.str(vararg keys: String): String? {
        for (k in keys) {
            val el = get(k) ?: continue
            if (el.isJsonNull || !el.isJsonPrimitive) continue
            val s = runCatching { el.asString }.getOrNull()
            if (!s.isNullOrBlank()) return s
        }
        return null
    }

    private fun JsonObject.num(vararg keys: String): Long? {
        for (k in keys) {
            val el: JsonElement = get(k) ?: continue
            if (el.isJsonNull || !el.isJsonPrimitive) continue
            runCatching { el.asLong }.getOrNull()?.let { return it }
            runCatching { el.asString.trim().toLong() }.getOrNull()?.let { return it }
        }
        return null
    }
}

/** Version comparison shared by the manifest parser and the update engine. */
object Versions {
    /** @return -1 when [a] is older, 0 when equal, 1 when newer. */
    fun compare(a: String?, b: String?): Int {
        val pa = split(a)
        val pb = split(b)
        val n = maxOf(pa.first.size, pb.first.size)
        for (i in 0 until n) {
            val x = pa.first.getOrElse(i) { 0 }
            val y = pb.first.getOrElse(i) { 0 }
            if (x != y) return if (x < y) -1 else 1
        }
        if (pa.second == pb.second) return 0
        if (pa.second.isEmpty()) return 1
        if (pb.second.isEmpty()) return -1
        return if (pa.second < pb.second) -1 else 1
    }

    private fun split(raw: String?): Pair<List<Int>, String> {
        val s = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: ""
        val dash = s.indexOf('-')
        val core = if (dash >= 0) s.substring(0, dash) else s
        val pre = if (dash >= 0) s.substring(dash + 1) else ""
        val parts = core.split('.', '+', '_').map { p ->
            p.filter { it.isDigit() }.toIntOrNull() ?: 0
        }
        return parts to pre
    }
}
