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

    private const val POPOUT = "popout:"
    private const val NAME = "name:"
    const val PAGE_ICON_PACKAGE = "droiddeck.dock"

    var items by mutableStateOf(DEFAULT)
        private set
    var popouts by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    var names by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        items = dockParse(prefs.getString(KEY, null))
        popouts = prefs.all.mapNotNull { (k, v) ->
            if (!k.startsWith(POPOUT)) null else (v as? String)?.let { popoutParse(it) }?.takeIf { it.isNotEmpty() }?.let { k.removePrefix(POPOUT) to it }
        }.toMap()
        names = prefs.all.mapNotNull { (k, v) ->
            if (!k.startsWith(NAME)) null else (v as? String)?.takeIf { it.isNotBlank() }?.let { k.removePrefix(NAME) to it }
        }.toMap()
    }

    fun setName(context: Context, id: String, name: String?) {
        val value = name?.trim()?.takeIf { it.isNotEmpty() }
        names = if (value == null) names - id else names + (id to value)
        val edit = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (value == null) edit.remove(NAME + id) else edit.putString(NAME + id, value)
        edit.apply()
    }

    fun removeFromPopout(context: Context, itemId: String, id: String) =
        savePopout(context, itemId, popouts[itemId].orEmpty() - id)

    fun add(context: Context, id: String) = save(context, dockAdd(items, id))

    fun remove(context: Context, id: String) {
        save(context, dockRemove(items, id))
        if (id != LIBRARY) savePopout(context, id, emptyList())
    }

    fun togglePopout(context: Context, itemId: String, appId: String) =
        savePopout(context, itemId, popoutToggle(popouts[itemId].orEmpty(), appId))

    private fun savePopout(context: Context, itemId: String, apps: List<String>) {
        popouts = if (apps.isEmpty()) popouts - itemId else popouts + (itemId to apps)
        val edit = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (apps.isEmpty()) edit.remove(POPOUT + itemId) else edit.putString(POPOUT + itemId, apps.joinToString("\n"))
        edit.apply()
    }

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

internal fun popoutParse(raw: String): List<String> =
    raw.split('\n').map { it.trim() }.filter { dockApp(it) != null || dockPage(it) in DockPrefs.PAGES }.distinct()

// DroidDeck's per-app icon picker keys its choices by package/class; dock pages borrow a package name of their own.
internal fun pageIconApp(page: String, label: String) =
    com.droiddeck.launcher.HomeApp.LaunchableApp(label, DockPrefs.PAGE_ICON_PACKAGE, page, null)

internal fun popoutToggle(list: List<String>, appId: String): List<String> = if (appId in list) list - appId else list + appId

internal fun dockRemove(list: List<String>, id: String): List<String> = if (id == DockPrefs.LIBRARY) list else list - id

internal fun dockMove(list: List<String>, id: String, delta: Int): List<String> {
    val i = list.indexOf(id)
    val j = i + delta
    if (i < 0 || j !in list.indices) return list
    return list.toMutableList().apply { add(j, removeAt(i)) }
}
