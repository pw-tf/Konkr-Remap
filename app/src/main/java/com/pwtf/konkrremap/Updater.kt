package com.pwtf.konkrremap

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.json.JSONObject
import java.net.URL

/** Checks GitHub Releases for a newer signed APK and installs it with the system installer. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/pw-tf/Konkr-Remap/releases/latest"

    data class Release(val version: String, val apkUrl: String)

    /** Blocking. Returns the latest release if it's newer than this build. */
    fun check(): Release? = runCatching {
        val j = JSONObject(URL(LATEST).readText())
        val assets = j.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.getString("name").endsWith(".apk") }
        Release(j.getString("tag_name").removePrefix("v"), apk.getString("browser_download_url"))
    }.getOrNull()?.takeIf { newer(it.version, BuildConfig.VERSION_NAME) }

    /** Blocking. Downloads the APK; Android then asks the user to confirm (see MainActivity.onNewIntent). */
    fun install(ctx: Context, r: Release) {
        val installer = ctx.packageManager.packageInstaller
        val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        installer.openSession(id).use { s ->
            s.openWrite("update.apk", 0, -1).use { out ->
                URL(r.apkUrl).openStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val status = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            s.commit(status.intentSender)
        }
    }
}

/** "1.10.0" > "1.9.2". Non-numeric parts count as 0. */
fun newer(a: String, b: String): Boolean {
    val x = a.split('.').map { it.toIntOrNull() ?: 0 }
    val y = b.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}
