package com.droiddeck.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object HomeLibrary {
    private const val PREFS = "home_launcher"
    private const val ADDED = "library_added"
    private const val REMOVED = "library_removed"
    private const val EMULATORS = "library_emulators"

    var added by mutableStateOf<Set<String>>(emptySet())
        private set
    var removed by mutableStateOf<Set<String>>(emptySet())
        private set
    var emulators by mutableStateOf<Set<String>>(emptySet())
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        added = p.getStringSet(ADDED, emptySet()).orEmpty().toSet()
        removed = p.getStringSet(REMOVED, emptySet()).orEmpty().toSet()
        emulators = p.getStringSet(EMULATORS, emptySet()).orEmpty().toSet()
    }

    fun setEmulators(context: Context, packages: Set<String>) {
        load(context)
        if (packages == emulators) return
        emulators = packages
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(EMULATORS, packages).apply()
    }

    private fun listedByDefault(app: HomeApp.LaunchableApp, emulators: Set<String>): Boolean = app.game && app.packageName !in emulators

    fun includes(
        app: HomeApp.LaunchableApp, added: Set<String> = this.added, removed: Set<String> = this.removed, emulators: Set<String> = this.emulators,
    ): Boolean = app.key !in removed && (listedByDefault(app, emulators) || app.key in added)

    fun changed(
        added: Set<String>, removed: Set<String>, app: HomeApp.LaunchableApp, inLibrary: Boolean, emulators: Set<String> = emptySet(),
    ): Pair<Set<String>, Set<String>> {
        val default = listedByDefault(app, emulators)
        return (if (inLibrary && !default) added + app.key else added - app.key) to
            (if (!inLibrary && default) removed + app.key else removed - app.key)
    }

    fun set(context: Context, app: HomeApp.LaunchableApp, inLibrary: Boolean) {
        val (a, r) = changed(added, removed, app, inLibrary, emulators)
        added = a
        removed = r
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(ADDED, a).putStringSet(REMOVED, r).apply()
    }
}
