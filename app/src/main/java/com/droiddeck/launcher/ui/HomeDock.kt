package com.droiddeck.launcher.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droiddeck.launcher.core.AppUiPrefs

internal object DockPrefs {
    private const val FILE = "home_dock"
    private const val KEY = "items"
    const val LIBRARY = "page:games"
    val PAGES = listOf("games", "stores", AppUiPrefs.HOME_PAGE_APPS, "setup", "steam")
    val DEFAULT = PAGES.map { "page:$it" }

    var items by mutableStateOf(DEFAULT)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        items = dockParse(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun add(context: Context, id: String) = save(context, dockAdd(items, id))

    fun remove(context: Context, id: String) = save(context, dockRemove(items, id))

    fun move(context: Context, id: String, delta: Int) = save(context, dockMove(items, id, delta))

    private fun save(context: Context, next: List<String>) {
        items = next
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, next.joinToString("\n")).apply()
    }
}

internal fun dockPage(id: String): String? = id.takeIf { it.startsWith("page:") }?.removePrefix("page:")

internal fun dockApp(id: String): Pair<String, String>? =
    id.takeIf { it.startsWith("app:") }?.removePrefix("app:")?.split('/', limit = 2)?.takeIf { it.size == 2 && it.all(String::isNotEmpty) }?.let { it[0] to it[1] }

internal fun appDockId(packageName: String, className: String) = "app:$packageName/$className"

internal fun dockParse(raw: String?): List<String> {
    val list = raw?.split('\n')?.map { it.trim() }?.filter { dockPage(it) in DockPrefs.PAGES || dockApp(it) != null }?.distinct() ?: return DockPrefs.DEFAULT
    // The Library item always stays: it is the dock's only way back to the rest of DroidDeck.
    return if (DockPrefs.LIBRARY in list) list else listOf(DockPrefs.LIBRARY) + list
}

internal fun dockAdd(list: List<String>, id: String): List<String> = if (id in list) list else list + id

internal fun dockRemove(list: List<String>, id: String): List<String> = if (id == DockPrefs.LIBRARY) list else list - id

internal fun dockMove(list: List<String>, id: String, delta: Int): List<String> {
    val i = list.indexOf(id)
    val j = i + delta
    if (i < 0 || j !in list.indices) return list
    return list.toMutableList().apply { add(j, removeAt(i)) }
}
