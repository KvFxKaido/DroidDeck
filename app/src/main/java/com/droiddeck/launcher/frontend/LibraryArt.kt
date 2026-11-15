package com.droiddeck.launcher.frontend

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object LibraryArt {
    private const val ROOT = "library-art"

    var version by mutableIntStateOf(0)
        private set
    private val known = ConcurrentHashMap<String, Array<File?>>()

    internal fun folder(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8)).take(10).joinToString("") { "%02x".format(it) }

    private fun dir(context: Context, key: String) = File(context.filesDir, "$ROOT/${folder(key)}")

    private fun sourceFile(context: Context, key: String, slot: AddedGameArt.Slot) = File(dir(context, key), "source-${slot.key}")

    fun chosen(context: Context, key: String, slot: AddedGameArt.Slot): File? =
        version.let { known.getOrPut("$key|${slot.key}") { arrayOf(AddedGameArt.chosen(dir(context, key), slot)) }[0] }

    internal fun prefer(tall: Boolean, cover: File?, wide: File?): File? = if (tall) cover ?: wide else wide ?: cover

    fun art(context: Context, key: String, tall: Boolean): File? =
        prefer(tall, chosen(context, key, AddedGameArt.Slot.COVER), chosen(context, key, AddedGameArt.Slot.WIDE))

    fun chosenRef(context: Context, key: String, slot: AddedGameArt.Slot): String =
        runCatching { sourceFile(context, key, slot).readText() }.getOrDefault("")

    fun choices(context: Context, key: String, title: String, slot: AddedGameArt.Slot): AddedGameArt.Choices {
        val (found, status) = AddedGameArt.sgdbChoices(context, File(dir(context, key), "sgdb-id"), null, title, slot)
        return AddedGameArt.Choices(found, status)
    }

    fun set(context: Context, key: String, slot: AddedGameArt.Slot, option: AddedGameArt.Option): Boolean {
        if (!AddedGameArt.store(dir(context, key), slot, option)) return false
        runCatching { sourceFile(context, key, slot).writeText(option.full) }
        changed(key, slot)
        return true
    }

    fun reset(context: Context, key: String, slot: AddedGameArt.Slot) {
        AddedGameArt.chosen(dir(context, key), slot)?.delete()
        sourceFile(context, key, slot).delete()
        changed(key, slot)
    }

    private fun changed(key: String, slot: AddedGameArt.Slot) {
        known.remove("$key|${slot.key}")
        version++
    }
}
