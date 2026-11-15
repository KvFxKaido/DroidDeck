package com.droiddeck.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.frontend.AndroidEmulators
import com.droiddeck.launcher.frontend.CoverArt
import com.droiddeck.launcher.session.OfflineMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AndroidRoms {
    private const val PREFS = "home_launcher"
    private const val CHOSEN = "rom_emulators"

    var games by mutableStateOf<List<AndroidEmulators.Game>>(emptyList())
        private set
    var installed by mutableStateOf<Map<String, List<AndroidEmulators.Installed>>>(emptyMap())
        private set
    var chosen by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        chosen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(CHOSEN, emptySet()).orEmpty()
            .mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
    }

    fun choose(context: Context, system: String, packageName: String) {
        chosen = chosen + (system to packageName)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(CHOSEN, chosen.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    suspend fun refresh(context: Context, romsDir: String?) {
        load(context)
        val app = context.applicationContext
        val root = romsDir?.takeIf { it.isNotEmpty() }?.let(::File)
        val (found, emulators) = withContext(Dispatchers.IO) {
            val scanned = root?.takeIf { it.isDirectory }?.let { runCatching { AndroidEmulators.scan(it) }.getOrDefault(emptyList()) }.orEmpty()
            withArt(app, scanned) to if (scanned.isEmpty()) emptyMap() else installedEmulators(app)
        }
        games = found
        installed = emulators
        if (found.isEmpty() || OfflineMode.enabled(app)) return
        val fetched = withContext(Dispatchers.IO) {
            runCatching { CoverArt.fetchMissing(app, found.filter { it.art == null }.mapNotNull { AndroidEmulators.coverRom(it) }) }.getOrDefault(false)
        }
        if (fetched) games = withContext(Dispatchers.IO) { withArt(app, found) }
    }

    private fun withArt(context: Context, games: List<AndroidEmulators.Game>): List<AndroidEmulators.Game> =
        games.map { g -> if (g.art != null) g else AndroidEmulators.coverRom(g)?.let { CoverArt.cached(context, it) }?.let { g.withArt(it) } ?: g }

    private fun installedEmulators(context: Context): Map<String, List<AndroidEmulators.Installed>> {
        val pm = context.packageManager
        return AndroidEmulators.installed(
            hasActivity = { pkg, cls ->
                runCatching {
                    pm.getActivityInfo(ComponentName(pkg, cls), 0)
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrNull()
            },
            launcherPackages = {
                runCatching {
                    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.distinct()
                }.getOrDefault(emptyList())
            },
        )
    }
}

@Composable
internal fun RomEmulatorDialog(system: String, candidates: List<AndroidEmulators.Installed>, onPick: (AndroidEmulators.Installed) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rom_choose_emulator, system)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for (c in candidates) TextButton(onClick = { onPick(c) }, modifier = Modifier.focusGlide(CircleShape)) { Text(c.label) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_cancel)) } },
    )
}
