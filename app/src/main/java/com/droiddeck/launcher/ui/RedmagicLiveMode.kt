package com.droiddeck.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.RedmagicPerformance
import com.droiddeck.launcher.core.RedmagicPerformance.AppMode
import com.droiddeck.launcher.core.WirelessAdbFix

/**
 * REDMAGIC's performance mode in the in-game menu. A pick is saved as the running game's own mode (the same
 * setting its launch menu shows) and applied at once. The row's hint is what REDMAGIC holds now, read back.
 */
@Composable
internal fun RedmagicLiveGroup(host: MenuHost, chipModifier: Modifier) {
    val ctx = LocalContext.current
    val paired = remember { WirelessAdbFix.hasSavedPairing(ctx) }
    val game = RedmagicPerformance.activeGame
    var default by remember { mutableStateOf(RedmagicPerformance.default(ctx)) }
    var own by remember(game) { mutableStateOf(game?.let { RedmagicPerformance.forGame(ctx, it) }) }
    var held by remember { mutableStateOf<AppMode?>(null) }
    var reads by remember { mutableIntStateOf(0) }
    LaunchedEffect(paired, reads) {
        if (paired) RedmagicPerformance.readAppMode(ctx, ctx.packageName) { m -> liveMainThread.post { held = m } }
    }

    val defaultLabel = stringResource(R.string.redmagic_perf_default, redmagicModeLabel(default))
    val labels = RedmagicPerformance.CHOICES.map { it to redmagicModeLabel(it) }
    val options: List<Pair<Int?, String>> = (if (game != null) listOf<Pair<Int?, String>>(null to defaultLabel) else emptyList()) + labels
    val selected: Int? = if (game != null) own else default
    val reading = when {
        !paired -> stringResource(R.string.redmagic_perf_unpaired)
        else -> when (val h = held) {
            null -> stringResource(R.string.redmagic_app_reading)
            is AppMode.Held -> if (h.diablo) stringResource(R.string.redmagic_live_diablo)
                else stringResource(R.string.redmagic_app_held, liveHeldLabel(h.value))
            AppMode.NotListed -> stringResource(R.string.redmagic_live_not_listed)
            is AppMode.Failed -> stringResource(R.string.redmagic_app_failed, h.why)
        }
    }

    SettingsGroup(stringResource(R.string.redmagic_perf_title)) {
        ChoiceRow(
            host, "redmagic-mode", stringResource(R.string.redmagic_perf_title),
            reading,
            options, selected, enabled = paired,
            note = stringResource(if (game != null) R.string.redmagic_live_hint_game else R.string.redmagic_live_hint_default),
            chipModifier = chipModifier,
        ) { mode ->
            if (game != null) own = mode else default = mode ?: RedmagicPerformance.LEAVE
            RedmagicPerformance.chooseNow(ctx, mode)
            held = null
            reads++
        }
    }
}

@Composable
private fun liveHeldLabel(value: String): String = when (value.toIntOrNull()) {
    RedmagicPerformance.ECO, RedmagicPerformance.BALANCE, RedmagicPerformance.RISE -> redmagicModeLabel(value.toInt())
    else -> stringResource(R.string.redmagic_app_other, value)
}

private val liveMainThread = android.os.Handler(android.os.Looper.getMainLooper())
