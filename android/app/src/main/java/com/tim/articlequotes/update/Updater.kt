package com.tim.articlequotes.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tim.articlequotes.BuildConfig
import com.tim.articlequotes.Notifications
import com.tim.articlequotes.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** A newer build published on the GitHub "latest" release. */
data class AppUpdate(val versionCode: Int, val versionName: String, val apkUrl: String) {
    fun toJson(): String = JSONObject().put("code", versionCode).put("name", versionName).put("url", apkUrl).toString()

    companion object {
        fun fromJson(s: String): AppUpdate? = runCatching {
            val o = JSONObject(s); AppUpdate(o.getInt("code"), o.getString("name"), o.getString("url"))
        }.getOrNull()
    }
}

/**
 * Sideloaded apps never update themselves. This checks the GitHub release once a day and,
 * when a newer build exists, downloads it and hands it to Android's installer.
 * The release title carries the build number, e.g. "Article Quotes 1.3 (build 4)".
 */
object Updater {
    private const val RELEASE_API = "https://api.github.com/repos/timjmills/article-quotes/releases/tags/latest"
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    fun pending(prefs: Prefs): AppUpdate? =
        AppUpdate.fromJson(prefs.availableUpdate)?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }

    /** Returns the newer build if there is one. Network errors return null quietly. */
    suspend fun check(ctx: Context): AppUpdate? = withContext(Dispatchers.IO) {
        val prefs = Prefs(ctx)
        prefs.lastUpdateCheck = System.currentTimeMillis()
        val body = runCatching {
            client.newCall(Request.Builder().url(RELEASE_API).header("Accept", "application/vnd.github+json").build())
                .execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return@withContext pending(prefs)
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext pending(prefs)
        val title = o.optString("name")
        val code = Regex("""build\s+(\d+)""", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: return@withContext null
        val name = Regex("""(\d+\.\d+(?:\.\d+)?)""").find(title)?.groupValues?.get(1) ?: "build $code"
        val assets = o.optJSONArray("assets")
        var url: String? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break }
        }
        if (url == null) return@withContext null
        val u = AppUpdate(code, name, url)
        prefs.availableUpdate = if (code > BuildConfig.VERSION_CODE) u.toJson() else ""
        u.takeIf { code > BuildConfig.VERSION_CODE }
    }

    /** Android asks once per app before it may install other APKs. */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Downloads the APK, reporting progress 0..1. Returns the file, or null on failure. */
    suspend fun download(ctx: Context, u: AppUpdate, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "ArticleQuotes-${u.versionCode}.apk")
        runCatching {
            client.newCall(Request.Builder().url(u.apkUrl).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val body = r.body ?: return@withContext null
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            output.write(buf, 0, n); done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                        if (total > 0 && done != total) { out.delete(); return@withContext null }
                    }
                }
            }
            out
        }.getOrNull()
    }

    fun installIntent(ctx: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Opens Android's installer. Android blocks this from the background, so if the app is no
     * longer in front (you switched away during the download), a notification does it instead.
     */
    fun install(ctx: Context, apk: File, inForeground: Boolean) {
        val i = installIntent(ctx, apk)
        if (inForeground) ctx.startActivity(i) else Notifications.showInstallReady(ctx, i)
    }
}
