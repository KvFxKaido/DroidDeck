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
import kotlinx.coroutines.withContext

object LibraryArt {
    private const val TAG = "LibraryArt"
    private const val ROOT = "library-art"
    private const val OFF = "auto-off"
    private const val TRIED = "auto-tried"
    private const val RETRY_AFTER_MS = 7L * 24 * 3600 * 1000
    private const val PER_PASS = 40
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

    private fun changed(key: String) {
        for (s in SLOTS) known.remove("$key|${s.key}")
        version++
    }

    internal fun needsFetch(dir: File, now: Long, keyChangedAt: Long): Boolean {
        if (File(dir, OFF).exists() || SLOTS.all { fetched(dir, it) != null }) return false
        val tried = File(dir, TRIED)
        return !(tried.isFile && tried.lastModified() >= keyChangedAt && now - tried.lastModified() < RETRY_AFTER_MS)
    }

    suspend fun fetchAll(context: Context, wanted: List<Wanted>) {
        val app = context.applicationContext
        if (wanted.isEmpty() || OfflineMode.enabled(app) || !SessionPrefs.addedGamesArt(app)) return
        while (withContext(Dispatchers.IO) { fetchMissing(app, wanted) } >= PER_PASS) delay(500)
    }

    private fun fetchMissing(context: Context, wanted: List<Wanted>): Int {
        val key = SteamGridDb.key(context)
        if (key.isEmpty()) return 0
        val keyChangedAt = SteamGridDb.keyChangedAt(context)
        var tried = 0
        for (w in wanted) {
            if (tried >= PER_PASS) break
            val dir = dir(context, w.key)
            if (!needsFetch(dir, System.currentTimeMillis(), keyChangedAt)) continue
            dir.mkdirs()
            try {
                val id = AddedGameArt.remembered(File(dir, "sgdb-id"), keyChangedAt) { SteamGridDb.gameId(key, null, w.title)?.toString() }?.toIntOrNull()
                if (id != null && SteamGridDb.download(key, id, dir, SLOTS)) changed(w.key)
                File(dir, TRIED).writeText("")
                tried++
            } catch (e: Exception) {
                Log.w(TAG, "${w.title}: SteamGridDB failed (${e.javaClass.simpleName}); not asking again this pass")
                return 0
            }
        }
        return tried
    }
}
