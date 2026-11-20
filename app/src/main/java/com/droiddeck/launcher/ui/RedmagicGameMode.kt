package com.droiddeck.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.RedmagicPerformance
import com.droiddeck.launcher.core.RedmagicPerformance.AppMode
import com.droiddeck.launcher.frontend.AndroidEmulators

/** Whose performance mode a Library entry's menu sets. */
internal sealed interface ModeTarget {
    /** A game DroidDeck runs in its own session: its mode is set while it runs and put back after, like a Steam game's. */
    data class Session(val key: String) : ModeTarget

    /** An Android app: REDMAGIC keeps its mode itself, so this edits that. [forRom] names the ROM it would open. */
    data class App(val pkg: String, val label: String, val forRom: String? = null) : ModeTarget
}

internal fun modeTarget(e: LibraryEntry, installed: Map<String, List<AndroidEmulators.Installed>>, gameChosen: Map<String, String>, chosen: Map<String, String>): ModeTarget? {
    e.deck?.let { return ModeTarget.Session(e.key) }
    e.app?.let { return ModeTarget.App(it.packageName, it.label) }
    val rom = e.rom ?: return null
    val target = AndroidEmulators.choose(installed[rom.system.id].orEmpty(), gameChosen[rom.file.path], chosen[rom.system.id]) ?: return null
    return ModeTarget.App(target.packageName, target.label, forRom = e.title)
}

@Composable
private fun heldLabel(value: String): String = when (value.toIntOrNull()) {
    RedmagicPerformance.ECO, RedmagicPerformance.BALANCE, RedmagicPerformance.RISE -> redmagicModeLabel(value.toInt())
    else -> stringResource(R.string.redmagic_app_other, value)
}

@Composable
internal fun RedmagicModeDialog(target: ModeTarget, paired: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.redmagic_perf_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (target) {
                    is ModeTarget.Session -> SessionChoices(target.key, paired, onDismiss)
                    is ModeTarget.App -> AppChoices(target, paired)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ModeNote(text: String) = Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SessionChoices(key: String, paired: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val default = RedmagicPerformance.default(ctx)
    val own = RedmagicPerformance.forGame(ctx, key)
    ModeNote(stringResource(if (paired) R.string.redmagic_perf_hint else R.string.redmagic_perf_unpaired))
    val pick: (Int?) -> Unit = { RedmagicPerformance.setForGame(ctx, key, it); onDone() }
    TextButton(onClick = { pick(null) }, modifier = Modifier.focusGlide(CircleShape)) {
        val label = stringResource(R.string.redmagic_perf_default, redmagicModeLabel(default))
        Text(if (own == null) stringResource(R.string.redmagic_perf_selected, label) else label)
    }
    for (mode in RedmagicPerformance.CHOICES) TextButton(onClick = { pick(mode) }, modifier = Modifier.focusGlide(CircleShape)) {
        val label = redmagicModeLabel(mode)
        Text(if (own == mode) stringResource(R.string.redmagic_perf_selected, label) else label)
    }
}

@Composable
private fun AppChoices(target: ModeTarget.App, paired: Boolean) {
    val ctx = LocalContext.current
    var state by remember(target.pkg) { mutableStateOf<AppMode?>(null) }
    var writing by remember(target.pkg) { mutableStateOf(false) }
    val post: (AppMode) -> Unit = { m -> mainThread.post { state = m; writing = false } }
    LaunchedEffect(target.pkg, paired) { if (paired) RedmagicPerformance.readAppMode(ctx, target.pkg, post) }
    ModeNote(
        if (target.forRom != null) stringResource(R.string.redmagic_app_rom_hint, target.label, target.forRom)
        else stringResource(R.string.redmagic_app_hint, target.label),
    )
    if (!paired) {
        ModeNote(stringResource(R.string.redmagic_perf_unpaired))
        return
    }
    when (val s = state) {
        null -> ModeNote(stringResource(R.string.redmagic_app_reading))
        is AppMode.Failed -> ModeNote(stringResource(R.string.redmagic_app_failed, s.why))
        AppMode.NotListed -> ModeNote(stringResource(R.string.redmagic_app_not_listed, target.label))
        is AppMode.Held -> {
            ModeNote(stringResource(R.string.redmagic_app_held, heldLabel(s.value)))
            if (s.diablo) ModeNote(stringResource(R.string.redmagic_app_diablo))
            else for (mode in RedmagicPerformance.CHOICES - RedmagicPerformance.LEAVE) {
                TextButton(
                    onClick = { writing = true; RedmagicPerformance.writeAppMode(ctx, target.pkg, mode, post) },
                    enabled = !writing, modifier = Modifier.focusGlide(CircleShape),
                ) {
                    val label = redmagicModeLabel(mode)
                    Text(if (s.value == mode.toString()) stringResource(R.string.redmagic_perf_selected, label) else label)
                }
            }
        }
    }
}

private val mainThread = android.os.Handler(android.os.Looper.getMainLooper())
