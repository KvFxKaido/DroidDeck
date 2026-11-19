package com.droiddeck.launcher.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal object EntryNames {
    private const val FILE = "entry_names"

    var names by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        names = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? String)?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
    }

    fun set(context: Context, key: String, name: String?) {
        load(context)
        val value = name?.trim()?.takeIf { it.isNotEmpty() }
        val edit = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (value == null) edit.remove(key) else edit.putString(key, value)
        edit.apply()
        names = if (value == null) names - key else names + (key to value)
    }
}

internal fun renamedEntries(entries: List<LibraryEntry>, names: Map<String, String>): List<LibraryEntry> =
    if (names.isEmpty()) entries
    else entries.map { e -> names[e.key]?.let { LibraryEntry(e.key, e.source, it, e.game, e.item, e.installed, e.app, e.rom, e.deck) } ?: e }
