package com.droiddeck.launcher.core

import android.content.Context
import android.util.Log
import com.droiddeck.launcher.HomeApp
import java.util.concurrent.Executors

object RedmagicPerformance {
    private const val TAG = "RedmagicPerformance"
    const val LEAVE = 0
    const val ECO = 100
    const val BALANCE = 200
    const val RISE = 300
    val CHOICES = listOf(LEAVE, ECO, BALANCE, RISE)

    private const val MODES = "NubiaperformanceMode"
    private const val DIABLO = "db_game_chicken_value"
    private const val SPLIT = "---droiddeck---"
    private const val SETTINGS = "redmagic_performance"
    private const val STATE = "redmagic-performance-state"
    private const val APPLIED = "applied"
    private const val PREVIOUS = "previous"
    private val SAFE = Regex("[A-Za-z0-9_.+,-]+")
    private val VALUE = Regex("[0-9]{1,5}")

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "redmagic-performance").apply { isDaemon = true } }
    @Volatile private var supported: Boolean? = null
    @Volatile private var wanted: Int? = null
    @Volatile private var inSession = false
    @Volatile private var retryAfter = 0L
    private const val RETRY_MS = 60_000L

    fun supported(context: Context): Boolean =
        supported ?: runCatching { HomeApp.hasGameSpace(context.applicationContext) }.getOrDefault(false).also { supported = it }

    private fun settings(context: Context) = context.applicationContext.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
    private fun state(context: Context) = context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE)

    fun default(context: Context): Int = settings(context).getInt("mode", LEAVE).takeIf { it in CHOICES } ?: LEAVE

    fun setDefault(context: Context, mode: Int) {
        require(mode in CHOICES)
        settings(context).edit().putInt("mode", mode).apply()
    }

    fun forGame(context: Context, game: String): Int? =
        settings(context).takeIf { it.contains("game.$game.mode") }?.getInt("game.$game.mode", LEAVE)?.takeIf { it in CHOICES }

    fun setForGame(context: Context, game: String, mode: Int?) {
        require(mode == null || mode in CHOICES)
        settings(context).edit().apply { if (mode == null) remove("game.$game.mode") else putInt("game.$game.mode", mode) }.apply()
    }

    internal fun pick(forGame: Int?, default: Int): Int = forGame ?: default

    internal fun readCommand() = "settings get global $MODES; echo $SPLIT; settings get global $DIABLO"

    internal fun writeCommand(list: String): String {
        require(SAFE.matches(list))
        return "settings put global $MODES '$list'"
    }

    internal class Read(val list: String, val diablo: String)

    internal fun parse(output: String): Read? {
        val parts = output.split(SPLIT)
        if (parts.size != 2) return null
        val list = parts[0].trim('\n', '\r').takeIf { it != "null" && SAFE.matches(it) } ?: return null
        return Read(list, parts[1].trim('\n', '\r').takeIf { it != "null" }.orEmpty())
    }

    internal fun entry(list: String, pkg: String): String? =
        list.split(',').firstOrNull { it.startsWith("$pkg+") }?.removePrefix("$pkg+")?.takeIf { VALUE.matches(it) }

    internal fun withEntry(list: String, pkg: String, value: String): String? {
        if (!VALUE.matches(value) || entry(list, pkg) == null) return null
        return list.split(',').joinToString(",") { if (it.startsWith("$pkg+")) "$pkg+$value" else it }
    }

    internal fun inDiablo(diablo: String, pkg: String): Boolean = diablo.split(',').any { it == pkg }

    internal fun change(read: Read, pkg: String, value: String): String? {
        if (inDiablo(read.diablo, pkg) || entry(read.list, pkg) == value) return null
        return withEntry(read.list, pkg, value)
    }

    /** A launch extra naming the game a DroidDeck session runs when it has no Steam app id, for its mode. */
    const val EXTRA_GAME = "com.droiddeck.launcher.redmagic.GAME"

    /** What REDMAGIC holds for another app. Its own per-app mode applies while that app is in front, so nothing is put back. */
    sealed interface AppMode {
        data class Held(val value: String, val diablo: Boolean) : AppMode
        object NotListed : AppMode
        data class Failed(val why: String) : AppMode
    }

    internal fun appMode(read: Read, pkg: String): AppMode =
        entry(read.list, pkg)?.let { AppMode.Held(it, inDiablo(read.diablo, pkg)) } ?: AppMode.NotListed

    fun readAppMode(context: Context, pkg: String, done: (AppMode) -> Unit) {
        val app = context.applicationContext
        worker.execute { done(readApp(app, pkg)) }
    }

    fun writeAppMode(context: Context, pkg: String, mode: Int, done: (AppMode) -> Unit) {
        require(mode in CHOICES && mode != LEAVE)
        val app = context.applicationContext
        worker.execute {
            val now = runCatching {
                val read = parse(WirelessAdbFix.shell(app, readCommand())) ?: error("the mode list could not be read")
                change(read, pkg, mode.toString())?.let { WirelessAdbFix.shell(app, writeCommand(it)) }
            }.exceptionOrNull()
            done(now?.let { AppMode.Failed(it.message ?: it.javaClass.simpleName) } ?: readApp(app, pkg))
        }
    }

    private fun readApp(app: Context, pkg: String): AppMode = runCatching {
        appMode(parse(WirelessAdbFix.shell(app, readCommand())) ?: error("the mode list could not be read"), pkg)
    }.getOrElse { AppMode.Failed(it.message ?: it.javaClass.simpleName) }

    fun onActiveGame(context: Context, game: String?) {
        val app = context.applicationContext
        if (!supported(app)) return
        inSession = true
        val want = pick(game?.takeIf { it.isNotBlank() }?.let { forGame(app, it) }, default(app))
        if (want == wanted || System.currentTimeMillis() < retryAfter) return
        wanted = want
        worker.execute { if (want == LEAVE) restore(app) else apply(app, want) }
    }

    fun recoverAsync(context: Context) {
        if (!inSession) restoreAsync(context)
    }

    fun restoreAsync(context: Context) {
        val app = context.applicationContext
        inSession = false
        wanted = null
        worker.execute { restore(app) }
    }

    private fun apply(app: Context, mode: Int) {
        runCatching {
            val read = parse(WirelessAdbFix.shell(app, readCommand())) ?: error("the mode list could not be read")
            val current = entry(read.list, app.packageName) ?: error("no entry for ${app.packageName}")
            if (inDiablo(read.diablo, app.packageName)) {
                Log.i(TAG, "Diablo is on; the mode is left alone")
                return
            }
            if (!state(app).getBoolean(APPLIED, false)) {
                check(state(app).edit().putBoolean(APPLIED, true).putString(PREVIOUS, current).commit()) { "previous mode not saved" }
            }
            change(read, app.packageName, mode.toString())?.let { WirelessAdbFix.shell(app, writeCommand(it)) }
        }.onSuccess { Log.i(TAG, "performance mode $mode while this game runs") }
            .onFailure {
                wanted = null
                retryAfter = System.currentTimeMillis() + RETRY_MS
                Log.w(TAG, "mode not set: ${it.message}")
            }
    }

    private fun restore(app: Context) {
        if (!state(app).getBoolean(APPLIED, false)) return
        val previous = state(app).getString(PREVIOUS, null)
        runCatching {
            if (previous != null) {
                val read = parse(WirelessAdbFix.shell(app, readCommand())) ?: error("the mode list could not be read")
                change(read, app.packageName, previous)?.let { WirelessAdbFix.shell(app, writeCommand(it)) }
            }
        }.onSuccess {
            state(app).edit().putBoolean(APPLIED, false).remove(PREVIOUS).commit()
            Log.i(TAG, "performance mode put back (${previous ?: "unchanged"})")
        }.onFailure { Log.w(TAG, "mode not put back yet: ${it.message}") }
    }
}
