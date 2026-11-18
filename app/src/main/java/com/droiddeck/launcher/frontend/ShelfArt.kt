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
    private const val ANY_ID = "sgdb-id-any"
    private const val OFF = "auto-off"
    private const val SOURCE = "source-icon"
    private val SLOT = AddedGameArt.Slot.ICON
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

    internal fun shownIn(dir: File): File? = AddedGameArt.chosen(dir, SLOT) ?: if (File(dir, OFF).exists()) null else squareIn(dir)

    fun square(context: Context, key: String): File? =
        version.let { known.getOrPut(key) { arrayOf(shownIn(dir(context, key))) }[0] }

    fun chosen(context: Context, key: String): File? = AddedGameArt.chosen(dir(context, key), SLOT)

    fun chosenRef(context: Context, key: String): String =
        runCatching { File(dir(context, key), SOURCE).readText() }.getOrDefault("")

    fun set(context: Context, key: String, option: AddedGameArt.Option): Boolean {
        val dir = dir(context, key)
        if (!AddedGameArt.store(dir, SLOT, option)) return false
        runCatching { File(dir, SOURCE).writeText(option.full) }
        changed(key)
        return true
    }

    fun reset(context: Context, key: String) {
        resetIn(dir(context, key))
        changed(key)
    }

    // Each reset steps back once: your pick, then the automatic square, then the cropped cover, then automatic again.
    internal fun resetIn(dir: File) {
        val picked = AddedGameArt.chosen(dir, SLOT)
        when {
            picked != null -> { picked.delete(); File(dir, SOURCE).delete() }
            File(dir, OFF).exists() -> { File(dir, OFF).delete(); File(dir, TRIED).delete() }
            else -> { File(dir, SQUARE).delete(); dir.mkdirs(); File(dir, OFF).writeText("") }
        }
    }

    fun choices(context: Context, key: String, title: String, steamAppId: Int?): AddedGameArt.Choices {
        val apiKey = SteamGridDb.key(context)
        if (apiKey.isEmpty()) return AddedGameArt.Choices(emptyList(), SteamGridDb.Status.NO_KEY)
        val dir = dir(context, key).apply { mkdirs() }
        return try {
            val id = AddedGameArt.remembered(File(dir, ANY_ID), SteamGridDb.keyChangedAt(context), retryMiss = true) {
                SteamGridDb.gameId(apiKey, steamAppId, title)?.toString()
            }?.toIntOrNull() ?: return AddedGameArt.Choices(emptyList(), SteamGridDb.Status.NO_GAME)
            val icons = list(apiKey, ICONS.format(id))?.let(::iconOptions).orEmpty()
            val grids = list(apiKey, GRIDS.format(id))?.let { SteamGridDb.ranked(it, pngOnly = false) }.orEmpty()
                .map { (thumb, full) -> AddedGameArt.Option(AddedGameArt.SGDB, thumb, full) }
            AddedGameArt.Choices((icons + grids).distinctBy { it.full }, SteamGridDb.Status.OK)
        } catch (e: Exception) {
            AddedGameArt.Choices(emptyList(), (e as? SteamGridDb.Failed)?.status ?: SteamGridDb.Status.SERVER)
        }
    }

    private fun changed(key: String) {
        known.remove(key)
        version++
    }

    private val held = java.util.concurrent.atomic.AtomicInteger(0)

    fun hold() { held.incrementAndGet() }

    fun release() { held.updateAndGet { maxOf(0, it - 1) } }

    internal fun needsFetch(dir: File, now: Long, keyChangedAt: Long): Boolean {
        if (squareIn(dir) != null || File(dir, OFF).exists()) return false
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
                while (held.get() > 0) delay(ENTRY_PAUSE_MS)
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
                if (tmp.renameTo(File(dir, SQUARE))) changed(w.key)
            }
            File(dir, TRIED).writeText("")
            null
        } catch (e: Exception) {
            val status = (e as? SteamGridDb.Failed)?.status ?: SteamGridDb.Status.SERVER
            Log.w(TAG, "${w.title}: SteamGridDB failed ($status); stopping this pass")
            status
        }
    }

    private fun usableIcons(items: JSONArray): List<JSONObject> =
        (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            .filter { it.optString("url").startsWith("https://") }
            .filter { it.optString("mime") == "image/png" || it.optString("url").endsWith(".png", ignoreCase = true) }
            .filter { minOf(it.optInt("width"), it.optInt("height")) >= MIN_ICON }

    internal fun bestIcon(items: JSONArray): String? = usableIcons(items).maxByOrNull { it.optInt("score", 0) }?.optString("url")

    internal fun iconOptions(items: JSONArray): List<AddedGameArt.Option> =
        usableIcons(items).sortedByDescending { it.optInt("score", 0) }.map {
            val full = it.optString("url")
            AddedGameArt.Option(AddedGameArt.SGDB, it.optString("thumb").takeIf { t -> t.startsWith("https://") } ?: full, full)
        }

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
