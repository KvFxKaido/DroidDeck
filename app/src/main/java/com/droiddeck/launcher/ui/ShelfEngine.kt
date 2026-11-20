package com.droiddeck.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.frontend.AndroidEmulators
import com.droiddeck.launcher.frontend.Library

internal enum class EngineKind { PROTON, ANDROID, EMULATOR, CLOUD, PC }

/** What runs a shelf entry when it is opened. [name] is null when the kind's own label says it. */
internal class ShelfEngine(val kind: EngineKind, val name: String? = null)

internal fun shelfEngine(
    e: LibraryEntry,
    androidEmulators: Map<String, List<AndroidEmulators.Installed>>,
    gameChosen: Map<String, String>,
    systemChosen: Map<String, String>,
    deckNames: Map<String, String>,
): ShelfEngine? {
    val rom = e.rom
    val deck = e.deck
    val game = e.game
    return when {
        e.app != null -> ShelfEngine(EngineKind.ANDROID)
        // The same choice the shelf makes when the ROM is opened; with none made yet, the system is all that is known.
        rom != null -> ShelfEngine(
            EngineKind.EMULATOR,
            AndroidEmulators.choose(androidEmulators[rom.system.id].orEmpty(), gameChosen[rom.file.path], systemChosen[rom.system.id])?.label ?: rom.system.name,
        )
        deck != null -> ShelfEngine(EngineKind.EMULATOR, deckNames[deck.emulatorId] ?: deck.emulatorId)
        game?.library == Library.XBOX_CLOUD -> ShelfEngine(EngineKind.CLOUD)
        // A Proton prefix exists once the game has run under Proton; before that, only "PC" is known for sure.
        game?.protonPrefix != null -> ShelfEngine(EngineKind.PROTON)
        game != null || e.item != null -> ShelfEngine(EngineKind.PC)
        else -> null
    }
}

private fun EngineKind.colour(): Color = when (this) {
    EngineKind.PROTON -> Color(0xFF5AA2FF)
    EngineKind.ANDROID -> Color(0xFF3DDC84)
    EngineKind.EMULATOR -> Color(0xFFFFB340)
    EngineKind.CLOUD -> Color(0xFF8FD3FF)
    EngineKind.PC -> Color(0xFF9A9AA2)
}

@Composable
private fun ShelfEngine.label(): String = name ?: stringResource(
    when (kind) {
        EngineKind.PROTON -> R.string.home_shelf_engine_proton
        EngineKind.ANDROID -> R.string.home_shelf_engine_android
        EngineKind.CLOUD -> R.string.home_shelf_engine_cloud
        EngineKind.PC, EngineKind.EMULATOR -> R.string.home_shelf_engine_pc
    },
)

/** The badge on the focused tile: a dot in the engine's colour and its name. */
@Composable
internal fun ShelfEngineChip(engine: ShelfEngine, tile: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color(0xC8080809), RoundedCornerShape(tile * 0.06f))
            .padding(start = tile * 0.04f, end = tile * 0.055f, top = tile * 0.018f, bottom = tile * 0.018f),
        horizontalArrangement = Arrangement.spacedBy(tile * 0.035f), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(tile * 0.055f).background(engine.kind.colour(), CircleShape))
        Text(
            engine.label(), color = Color(0xFFF4F4F6), fontWeight = FontWeight.Bold, fontSize = (tile.value * 0.078f).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The focused game's title above the dock, with what runs it. */
@Composable
internal fun ShelfFocusLine(title: String, engine: ShelfEngine?, modifier: Modifier = Modifier) {
    val shadow = TextStyle(shadow = Shadow(Color.Black, blurRadius = 8f))
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = shadow, modifier = Modifier.weight(1f, fill = false))
        if (engine != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(engine.kind.colour(), CircleShape))
            Text(engine.label(), color = Color(0xFFCFCFD5), fontSize = 14.sp, maxLines = 1, style = shadow)
        }
    }
}
