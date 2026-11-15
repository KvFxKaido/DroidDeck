package com.droiddeck.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

@Composable
internal fun redmagicModeLabel(mode: Int): String = stringResource(
    when (mode) {
        RedmagicPerformance.ECO -> R.string.redmagic_perf_eco
        RedmagicPerformance.BALANCE -> R.string.redmagic_perf_balance
        RedmagicPerformance.RISE -> R.string.redmagic_perf_rise
        else -> R.string.redmagic_perf_leave
    },
)

@Composable
internal fun RedmagicPerformanceCard(
    game: String?, paired: Boolean, modifier: Modifier,
    card: @Composable (value: String, onClick: () -> Unit) -> Unit,
) {
    val ctx = LocalContext.current
    var default by remember { mutableStateOf(RedmagicPerformance.default(ctx)) }
    var own by remember(game) { mutableStateOf(game?.let { RedmagicPerformance.forGame(ctx, it) }) }
    var open by remember { mutableStateOf(false) }
    val shown = if (game == null) default else own ?: default
    Box(modifier) { card(redmagicModeLabel(shown)) { open = true } }
    if (!open) return
    val pick: (Int?) -> Unit = { mode ->
        open = false
        if (game == null) {
            val value = mode ?: RedmagicPerformance.LEAVE
            RedmagicPerformance.setDefault(ctx, value)
            default = value
        } else {
            RedmagicPerformance.setForGame(ctx, game, mode)
            own = mode
        }
    }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.redmagic_perf_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(if (paired) R.string.redmagic_perf_hint else R.string.redmagic_perf_unpaired),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (game != null) TextButton(onClick = { pick(null) }, modifier = Modifier.focusGlide(CircleShape)) {
                    val label = stringResource(R.string.redmagic_perf_default, redmagicModeLabel(default))
                    Text(if (own == null) stringResource(R.string.redmagic_perf_selected, label) else label)
                }
                for (mode in RedmagicPerformance.CHOICES) TextButton(onClick = { pick(mode) }, modifier = Modifier.focusGlide(CircleShape)) {
                    val label = redmagicModeLabel(mode)
                    val current = if (game == null) default == mode else own == mode
                    Text(if (current) stringResource(R.string.redmagic_perf_selected, label) else label)
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_cancel)) } },
    )
}
