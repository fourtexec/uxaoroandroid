package org.uxaoro.app.upd

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded APK over to Android's package installer.
 *
 * Android never lets a normal app install a package silently: the user confirms
 * the installation in the system installer. On Android 8+ the app additionally
 * needs the "install unknown apps" permission for its own package, which is
 * [canInstall] / [requestInstallPermission].
 */
object ApkInstaller {

    const val APK_MIME = "application/vnd.android.package-archive"

    /** True when the system will accept an install intent from Uxaoro. */
    fun canInstall(ctx: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    } catch (_: Exception) {
        true
    }

    /** Opens the system screen where the user grants Uxaoro the install right. */
    fun requestInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }
    }

    /**
     * The size of every APK Uxaoro knows about, so the UI can say how much space
     * an update needs.
     */
    fun apkDir(ctx: Context): File = File(ctx.cacheDir, "updates").apply { mkdirs() }

    fun downloadedApk(ctx: Context, update: AndroidUpdate?): File? {
        val version = update?.version ?: return null
        val f = File(apkDir(ctx), "uxaoro-$version.apk")
        return f.takeIf { it.isFile && it.length() > 0 }
    }

    /**
     * Launches the system installer for [apk].
     *
     * @return null when the installer was opened, otherwise a message for the UI.
     */
    fun install(ctx: Context, apk: File): String? {
        if (!apk.isFile || apk.length() == 0L) return "The downloaded file is missing. Download the update again."
        if (!canInstall(ctx)) {
            requestInstallPermission(ctx)
            return "Allow Uxaoro to install unknown apps, then tap Install again."
        }
        return try {
            val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            null
        } catch (t: Throwable) {
            "Android could not open the package installer: ${t.message ?: t::class.java.simpleName}"
        }
    }
}
