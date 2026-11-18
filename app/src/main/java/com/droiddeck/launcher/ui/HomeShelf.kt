package com.droiddeck.launcher.ui

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.droiddeck.launcher.HomeLibrary
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.AppUiPrefs
import com.droiddeck.launcher.frontend.AndroidEmulators
import com.droiddeck.launcher.stores.Store
import com.droiddeck.launcher.stores.StoresState

internal object ShelfState {
    var showing by mutableStateOf(false)
    var lastFocused: String? = null
    private var returnTo: String? = null

    fun leave(to: String?) {
        returnTo = to
        showing = false
    }

    fun takeReturn(): String? = returnTo.also { returnTo = null }
}

internal fun shelfSortKey(title: String): String =
    title.lowercase().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim().removePrefix("the ")

internal fun shelfEntries(all: List<LibraryEntry>, favorites: Set<String>): List<LibraryEntry> =
    all.filter { it.favoriteKey in favorites }.distinctBy { it.favoriteKey }.sortedWith(compareBy({ shelfSortKey(it.title) }, { it.key }))

private const val SHELF_ROWS = 3
private const val SHELF_GAP = 0.25f
private val SHELF_RING = Color(0xFFF2F2F2)

@Composable
internal fun HomeShelf(s: FrontEndState, a: FrontEndActions) {
    val ctx = LocalContext.current
    ShelfWallpaper()
    LaunchedEffect(s.navigate) {
        when (s.navigate) {
            null -> Unit
            AppUiPrefs.HOME_PAGE_SHELF -> a.onNavigated()
            else -> ShelfState.leave(null)
        }
    }
    BackHandler { }
    remember { LibraryFavorites.load(ctx) }
    remember { HomeLibrary.load(ctx) }
    remember { AndroidRoms.load(ctx) }
    LaunchedEffect(Unit) { StoresState.refresh(ctx) }
    LaunchedEffect(s.romsDir) { AndroidRoms.refresh(ctx, s.romsDir) }
    val signedIn = Store.entries.filter { StoresState.isSignedIn(it) }
    LaunchedEffect(signedIn) { signedIn.forEach { StoresState.loadLibrary(ctx, it) } }
    val owned = signedIn.associateWith { StoresState.library[it].orEmpty() }
    val installedStore = StoresState.installed.map { it.key }.toSet()
    val cloudRows = rememberXboxCloudRows(s)
    val favorites = LibraryFavorites.keys
    val entries = remember(s.steamGames, cloudRows, owned, installedStore, s.androidApps, HomeLibrary.added, HomeLibrary.removed, HomeLibrary.emulators, AndroidRoms.games, favorites) {
        shelfEntries(
            libraryEntries(s.steamGames + cloudRows, owned, installedStore) +
                androidEntries(s.androidApps, HomeLibrary.added, HomeLibrary.removed, HomeLibrary.emulators) + romEntries(AndroidRoms.games),
            favorites,
        )
    }
    val open: (LibraryEntry) -> Unit = { e ->
        ShelfState.lastFocused = e.key
        val app = e.app
        val rom = e.rom
        val game = e.game
        val item = e.item
        when {
            app != null -> a.onAndroidApp(app, null)
            rom != null -> {
                val candidates = AndroidRoms.installed[rom.system.id].orEmpty()
                val target = AndroidEmulators.choose(candidates, AndroidRoms.gameChosen[rom.file.path], AndroidRoms.chosen[rom.system.id])
                when {
                    candidates.isEmpty() -> android.widget.Toast.makeText(ctx, ctx.getString(R.string.library_rom_no_emulator, rom.system.name), android.widget.Toast.LENGTH_LONG).show()
                    target == null -> ShelfState.leave("games")
                    else -> a.onAndroidRom(rom, target)
                }
            }
            e.installed && game != null -> a.onSteamGame(game)
            e.installed && item != null -> {
                val store = Store.byId(e.source)
                val id = e.storeKey?.substringAfter(':')
                if (store != null && id != null) launchStoreGame(store, id, s, a) else ShelfState.leave(e.key)
            }
            else -> ShelfState.leave(e.key)
        }
    }
    val grid = rememberLazyGridState()
    val tileFocus = remember { FocusRequester() }
    val dockFocus = remember { FocusRequester() }
    val start = entries.indexOfFirst { it.key == ShelfState.lastFocused }.coerceAtLeast(0)
    LaunchedEffect(entries.isEmpty()) {
        if (entries.isNotEmpty()) grid.scrollToItem(start)
        repeat(30) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { (if (entries.isEmpty()) dockFocus else tileFocus).requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().drawBehind { drawRect(Color.Transparent, blendMode = BlendMode.Clear) }) {
        val dockHeight = 96.dp
        val gridHeight = maxHeight - dockHeight
        val tile = gridHeight / (SHELF_ROWS + (SHELF_ROWS + 1) * SHELF_GAP)
        val gap = tile * SHELF_GAP
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.home_shelf_empty), color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(bottom = dockHeight).padding(horizontal = 48.dp),
            )
        } else LazyHorizontalGrid(
            rows = GridCells.Fixed(SHELF_ROWS), state = grid,
            modifier = Modifier.fillMaxWidth().height(gridHeight),
            contentPadding = PaddingValues(horizontal = max(gap, 40.dp), vertical = gap),
            horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            itemsIndexed(entries, key = { _, e -> e.key }) { i, e ->
                ShelfTile(e, tile, if (i == start) Modifier.focusRequester(tileFocus) else Modifier) { open(e) }
            }
        }
        ShelfDock(s, a, dockFocus, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(dockHeight))
    }
}

@Composable
private fun ShelfTile(e: LibraryEntry, size: Dp, focus: Modifier, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "shelfTile")
    val art = remember(e.key, e.app, e.rom, e.game, e.item) { entryArt(ctx, e, tall = true) }
    val shape = RoundedCornerShape(size * 0.12f)
    Box(
        Modifier.size(size).zIndex(if (focused) 1f else 0f).graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.border(size * 0.022f, SHELF_RING, shape) else Modifier)
            .clip(shape).background(Color(0xFF161616))
            .semantics { contentDescription = e.title }
            .then(focus)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onOpen)
            .controllerConfirm(onClick = onOpen),
    ) {
        when (art) {
            null -> Text(
                e.title, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.1f).sp,
                textAlign = TextAlign.Center, lineHeight = (size.value * 0.12f).sp,
                modifier = Modifier.align(Alignment.Center).padding(size * 0.1f),
            )
            is Bitmap -> AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.align(Alignment.Center).size(size * 0.62f))
            else -> AsyncImage(
                model = art, contentDescription = null, contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(0f, -0.1f), modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private enum class DockGlyph { LIBRARY, STORES, APPS, SETTINGS, STEAM }

@Composable
private fun ShelfDock(s: FrontEndState, a: FrontEndActions, first: FocusRequester, modifier: Modifier) {
    val items = listOf(
        Triple(DockGlyph.LIBRARY, R.string.rail_games) { ShelfState.leave("games") },
        Triple(DockGlyph.STORES, R.string.rail_stores) { ShelfState.leave("stores") },
        Triple(DockGlyph.APPS, R.string.rail_apps) { ShelfState.leave(AppUiPrefs.HOME_PAGE_APPS) },
        Triple(DockGlyph.SETTINGS, R.string.rail_setup) { ShelfState.leave("setup") },
        Triple(DockGlyph.STEAM, R.string.rail_steam) { if (s.ready) a.onPlay() else ShelfState.leave("steam") },
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, (glyph, label, action) ->
            DockItem(glyph, stringResource(label), if (i == 0) Modifier.focusRequester(first) else Modifier, action)
        }
    }
}

@Composable
private fun DockItem(glyph: DockGlyph, label: String, focus: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "shelfDock")
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.size(64.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.background(Color.White.copy(alpha = 0.08f), shape).border(2.dp, SHELF_RING, shape) else Modifier)
            .clip(shape)
            .semantics { contentDescription = label }
            .then(focus)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .controllerConfirm(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).drawBehind { drawGlyph(glyph, if (glyph == DockGlyph.LIBRARY) Color(0xFF3B82F6) else Color(0xFFEDEDED)) })
    }
}

private fun DrawScope.drawGlyph(glyph: DockGlyph, color: Color) {
    val u = size.minDimension / 48f
    val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun circle(x: Float, y: Float, r: Float) = drawCircle(color, r * u, p(x, y), style = stroke)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)
    when (glyph) {
        DockGlyph.LIBRARY -> {
            drawRoundRect(color, p(7f, 7f), Size(34f * u, 34f * u), CornerRadius(10f * u), style = stroke)
            drawCircle(color, 8f * u, p(24f, 24f))
        }
        DockGlyph.STORES -> {
            drawPath(Path().apply { moveTo(9f * u, 14f * u); lineTo(39f * u, 14f * u); lineTo(36f * u, 40f * u); lineTo(12f * u, 40f * u); close() }, color, style = stroke)
            drawArc(color, 180f, 180f, false, p(17f, 7f), Size(14f * u, 14f * u), style = stroke)
            line(17f, 14f, 17f, 18f); line(31f, 14f, 31f, 18f)
        }
        DockGlyph.APPS -> { circle(15f, 15f, 7f); circle(33f, 15f, 7f); circle(15f, 33f, 7f); circle(33f, 33f, 7f) }
        DockGlyph.SETTINGS -> {
            circle(24f, 24f, 6f)
            line(24f, 5f, 24f, 11f); line(24f, 37f, 24f, 43f); line(5f, 24f, 11f, 24f); line(37f, 24f, 43f, 24f)
            line(10.6f, 10.6f, 14.8f, 14.8f); line(33.2f, 33.2f, 37.4f, 37.4f); line(10.6f, 37.4f, 14.8f, 33.2f); line(33.2f, 14.8f, 37.4f, 10.6f)
        }
        DockGlyph.STEAM -> {
            line(10f, 34f, 22f, 26f)
            circle(31f, 20f, 9f)
            drawCircle(color, 3.5f * u, p(31f, 20f))
            circle(12f, 34f, 5f)
        }
    }
}

@Composable
private fun ShelfWallpaper() {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    DisposableEffect(activity) {
        val window = activity?.window
        val background = window?.decorView?.background
        val format = window?.attributes?.format
        window?.setFormat(PixelFormat.TRANSLUCENT)
        window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window?.setBackgroundDrawable(background ?: ColorDrawable(android.graphics.Color.BLACK))
            if (format != null) window.setFormat(format)
        }
    }
}
