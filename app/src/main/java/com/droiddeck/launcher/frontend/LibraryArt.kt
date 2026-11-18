package com.droiddeck.launcher.frontend

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.droiddeck.launcher.session.OfflineMode
import com.droiddeck.launcher.session.SessionPrefs
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object LibraryArt {
    private const val TAG = "LibraryArt"
    private const val ROOT = "library-art"
    private const val OFF = "auto-off"
    private const val TRIED = "auto-tried"
    private const val RETRY_AFTER_MS = 7L * 24 * 3600 * 1000
    private val SLOTS = setOf(AddedGameArt.Slot.COVER, AddedGameArt.Slot.WIDE)

    class Wanted(val key: String, val title: String)

    var version by mutableIntStateOf(0)
        private set
    private val known = ConcurrentHashMap<String, Array<File?>>()

    internal fun folder(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8)).take(10).joinToString("") { "%02x".format(it) }

    private fun dir(context: Context, key: String) = File(context.filesDir, "$ROOT/${folder(key)}")

    private fun sourceFile(dir: File, slot: AddedGameArt.Slot) = File(dir, "source-${slot.key}")

    private fun fetched(dir: File, slot: AddedGameArt.Slot): File? = File(dir, SteamGridDb.cacheName(slot)).takeIf { it.isFile && it.length() > 0 }

    internal fun shown(dir: File, slot: AddedGameArt.Slot): File? = AddedGameArt.chosen(dir, slot) ?: fetched(dir, slot)

    fun chosen(context: Context, key: String, slot: AddedGameArt.Slot): File? = AddedGameArt.chosen(dir(context, key), slot)

    private fun shown(context: Context, key: String, slot: AddedGameArt.Slot): File? =
        version.let { known.getOrPut("$key|${slot.key}") { arrayOf(shown(dir(context, key), slot)) }[0] }

    internal fun prefer(tall: Boolean, cover: File?, wide: File?): File? = if (tall) cover ?: wide else wide ?: cover

    fun art(context: Context, key: String, tall: Boolean): File? =
        prefer(tall, shown(context, key, AddedGameArt.Slot.COVER), shown(context, key, AddedGameArt.Slot.WIDE))

    fun chosenRef(context: Context, key: String, slot: AddedGameArt.Slot): String =
        runCatching { sourceFile(dir(context, key), slot).readText() }.getOrDefault("")

    fun choices(context: Context, key: String, title: String, slot: AddedGameArt.Slot): AddedGameArt.Choices {
        val (found, status) = AddedGameArt.sgdbChoices(context, File(dir(context, key), "sgdb-id"), null, title, slot)
        return AddedGameArt.Choices(found, status)
    }

    fun set(context: Context, key: String, slot: AddedGameArt.Slot, option: AddedGameArt.Option): Boolean {
        val dir = dir(context, key)
        if (!AddedGameArt.store(dir, slot, option)) return false
        runCatching { sourceFile(dir, slot).writeText(option.full) }
        changed(key)
        return true
    }

    fun reset(context: Context, key: String, slot: AddedGameArt.Slot) {
        resetIn(dir(context, key), slot)
        changed(key)
    }

    internal fun resetIn(dir: File, slot: AddedGameArt.Slot) {
        val picked = AddedGameArt.chosen(dir, slot)
        if (picked != null) {
            picked.delete()
            sourceFile(dir, slot).delete()
            return
        }
        for (s in SLOTS) {
            File(dir, SteamGridDb.cacheName(s)).delete()
            File(dir, SteamGridDb.cacheName(s) + ".src").delete()
        }
        dir.mkdirs()
        File(dir, OFF).writeText("")
    }

    fun renamed(context: Context, key: String) {
        val dir = dir(context, key)
        if (SLOTS.any { AddedGameArt.chosen(dir, it) != null }) return
        for (s in SLOTS) {
            File(dir, SteamGridDb.cacheName(s)).delete()
            File(dir, SteamGridDb.cacheName(s) + ".src").delete()
        }
        File(dir, "sgdb-id").delete()
        File(dir, TRIED).delete()
        changed(key)
    }

    private fun changed(key: String) {
        for (s in SLOTS) known.remove("$key|${s.key}")
        version++
    }

    internal fun needsFetch(dir: File, now: Long, keyChangedAt: Long): Boolean {
        if (File(dir, OFF).exists() || SLOTS.all { fetched(dir, it) != null }) return false
        val tried = File(dir, TRIED)
        return !(tried.isFile && tried.lastModified() >= keyChangedAt && now - tried.lastModified() < RETRY_AFTER_MS)
    }

    private const val ENTRY_PAUSE_MS = 1_000L
    private const val HOLD_POLL_MS = 1_000L
    private const val LIMIT_PAUSE_MS = 10L * 60 * 1000

    @Volatile private var pausedUntil = 0L
    private val held = java.util.concurrent.atomic.AtomicInteger(0)
    private val pass = kotlinx.coroutines.sync.Mutex()

    fun hold() { held.incrementAndGet() }

    fun release() { held.updateAndGet { maxOf(0, it - 1) } }

    internal fun pauseAfter(status: SteamGridDb.Status?, now: Long): Long = if (status == SteamGridDb.Status.LIMIT) now + LIMIT_PAUSE_MS else 0L

    internal fun mayFetch(now: Long, pausedUntil: Long): Boolean = now >= pausedUntil

    suspend fun fetchAll(context: Context, wanted: List<Wanted>) {
        val app = context.applicationContext
        if (wanted.isEmpty() || OfflineMode.enabled(app) || !SessionPrefs.addedGamesArt(app)) return
        pass.withLock {
            if (!mayFetch(System.currentTimeMillis(), pausedUntil)) return
            val key = SteamGridDb.key(app)
            if (key.isEmpty()) return
            val keyChangedAt = SteamGridDb.keyChangedAt(app)
            for (w in wanted) {
                while (held.get() > 0) delay(HOLD_POLL_MS)
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
            val id = AddedGameArt.remembered(File(dir, "sgdb-id"), keyChangedAt) { SteamGridDb.gameId(key, null, w.title)?.toString() }?.toIntOrNull()
            if (id != null && SteamGridDb.download(key, id, dir, SLOTS)) changed(w.key)
            File(dir, TRIED).writeText("")
            null
        } catch (e: Exception) {
            val status = (e as? SteamGridDb.Failed)?.status ?: SteamGridDb.Status.SERVER
            Log.w(TAG, "${w.title}: SteamGridDB failed ($status); stopping this pass")
            status
        }
    }
}
