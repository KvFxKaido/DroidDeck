package com.droiddeck.launcher.frontend

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.droiddeck.launcher.session.OfflineMode
import com.droiddeck.launcher.session.SessionPrefs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object ShelfArt {
    private const val TAG = "ShelfArt"
    private const val ROOT = "shelf-art"
    private const val SQUARE = "square"
    private const val TRIED = "auto-tried"
    private const val ID = "sgdb-id"
    private const val RETRY_AFTER_MS = 7L * 24 * 3600 * 1000
    private const val ENTRY_PAUSE_MS = 1_000L
    private const val LIMIT_PAUSE_MS = 10L * 60 * 1000
    private const val ICONS = "https://www.steamgriddb.com/api/v2/icons/game/%d?nsfw=false&humor=false"
    private const val GRIDS = "https://www.steamgriddb.com/api/v2/grids/game/%d?dimensions=512x512,1024x1024&nsfw=false&humor=false"
    internal const val MIN_ICON = 256

    class Wanted(val key: String, val title: String, val steamAppId: Int?)

    var version by mutableIntStateOf(0)
        private set
    private val known = ConcurrentHashMap<String, Array<File?>>()

    private fun dir(context: Context, key: String) = File(context.filesDir, "$ROOT/${LibraryArt.folder(key)}")

    internal fun squareIn(dir: File): File? = File(dir, SQUARE).takeIf { it.isFile && it.length() > 0 }

    fun square(context: Context, key: String): File? =
        version.let { known.getOrPut(key) { arrayOf(squareIn(dir(context, key))) }[0] }

    internal fun needsFetch(dir: File, now: Long, keyChangedAt: Long): Boolean {
        if (squareIn(dir) != null) return false
        val tried = File(dir, TRIED)
        return !(tried.isFile && tried.lastModified() >= keyChangedAt && now - tried.lastModified() < RETRY_AFTER_MS)
    }

    internal fun pauseAfter(status: SteamGridDb.Status?, now: Long): Long = if (status == SteamGridDb.Status.LIMIT) now + LIMIT_PAUSE_MS else 0L

    @Volatile private var pausedUntil = 0L
    private val pass = kotlinx.coroutines.sync.Mutex()

    suspend fun fetchAll(context: Context, wanted: List<Wanted>) {
        val app = context.applicationContext
        if (wanted.isEmpty() || OfflineMode.enabled(app) || !SessionPrefs.addedGamesArt(app)) return
        pass.withLock {
            if (System.currentTimeMillis() < pausedUntil) return
            val key = SteamGridDb.key(app)
            if (key.isEmpty()) return
            val keyChangedAt = SteamGridDb.keyChangedAt(app)
            for (w in wanted) {
                val dir = dir(app, w.key)
                if (!withContext(Dispatchers.IO) { needsFetch(dir, System.currentTimeMillis(), keyChangedAt) }) continue
                val failed = withContext(Dispatchers.IO) { fetchOne(key, keyChangedAt, dir, w) }
                if (failed != null) {
                    pausedUntil = pauseAfter(failed, System.currentTimeMillis())
                    return
                }
                delay(ENTRY_PAUSE_MS)
            }
        }
    }

    private fun fetchOne(key: String, keyChangedAt: Long, dir: File, w: Wanted): SteamGridDb.Status? {
        dir.mkdirs()
        return try {
            // Exact matches only: a wrong square on the home screen is worse than a cropped right cover.
            val id = AddedGameArt.remembered(File(dir, ID), keyChangedAt) { SteamGridDb.exactGameId(key, w.steamAppId, w.title)?.toString() }?.toIntOrNull()
            val url = id?.let { list(key, ICONS.format(it))?.let(::bestIcon) ?: list(key, GRIDS.format(it))?.let { g -> SteamGridDb.best(g, pngOnly = false) } }
            val bytes = url?.let { AddedGameArt.get(it) }
            if (bytes != null && bytes.isNotEmpty()) {
                val tmp = File(dir, "$SQUARE.tmp")
                tmp.writeBytes(bytes)
                if (tmp.renameTo(File(dir, SQUARE))) {
                    known.remove(w.key)
                    version++
                }
            }
            File(dir, TRIED).writeText("")
            null
        } catch (e: Exception) {
            val status = (e as? SteamGridDb.Failed)?.status ?: SteamGridDb.Status.SERVER
            Log.w(TAG, "${w.title}: SteamGridDB failed ($status); stopping this pass")
            status
        }
    }

    internal fun bestIcon(items: JSONArray): String? =
        (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            .filter { it.optString("url").startsWith("https://") }
            .filter { it.optString("mime") == "image/png" || it.optString("url").endsWith(".png", ignoreCase = true) }
            .filter { minOf(it.optInt("width"), it.optInt("height")) >= MIN_ICON }
            .maxByOrNull { it.optInt("score", 0) }?.optString("url")

    private fun list(key: String, url: String): JSONArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "DroidDeck-launcher")
        c.setRequestProperty("Authorization", "Bearer $key")
        return try {
            val code = c.responseCode
            if (code == 200) JSONObject(c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)).takeIf { it.optBoolean("success", true) }?.optJSONArray("data")
            else {
                SteamGridDb.failure(code)?.let { throw SteamGridDb.Failed(it) }
                null
            }
        } catch (e: SteamGridDb.Failed) {
            throw e
        } catch (e: java.io.IOException) {
            throw SteamGridDb.Failed(SteamGridDb.Status.OFFLINE)
        } finally {
            c.disconnect()
        }
    }
}
