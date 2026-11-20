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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
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
import com.droiddeck.launcher.HomeApp
import com.droiddeck.launcher.HomeIcons
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import com.droiddeck.launcher.HomeLibrary
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.AppUiPrefs
import com.droiddeck.launcher.frontend.AndroidEmulators
import com.droiddeck.launcher.frontend.ShelfArt
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

    // Android can end the process while a game runs; the shelf is kept in the activity's saved state
    // so coming back lands where a warm return would.
    fun save(out: android.os.Bundle) {
        out.putBoolean(KEY_SHOWING, showing)
        lastFocused?.let { out.putString(KEY_FOCUSED, it) }
    }

    fun restore(saved: android.os.Bundle?, homePage: String, navigating: Boolean) {
        val was = saved?.takeIf { it.containsKey(KEY_SHOWING) }?.getBoolean(KEY_SHOWING)
        showing = showingOnCreate(was, showing, homePage, navigating)
        saved?.getString(KEY_FOCUSED)?.let { lastFocused = it }
    }

    fun showingOnCreate(saved: Boolean?, current: Boolean, homePage: String, navigating: Boolean): Boolean = when {
        saved != null -> saved
        !navigating && homePage == AppUiPrefs.HOME_PAGE_SHELF -> true
        else -> current
    }

    private const val KEY_SHOWING = "home_shelf_showing"
    private const val KEY_FOCUSED = "home_shelf_focused"
}

internal fun shelfSortKey(title: String): String =
    title.lowercase().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim().removePrefix("the ")

internal object ShelfPrefs {
    private const val FILE = "home_shelf"
    private const val INSTALLED = "installed_only"
    var installedOnly by mutableStateOf(false)
        private set
    private var loaded = false

    fun load(context: android.content.Context) {
        if (loaded) return
        loaded = true
        installedOnly = context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).getBoolean(INSTALLED, false)
    }

    fun setInstalledOnly(context: android.content.Context, value: Boolean) {
        installedOnly = value
        context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).edit().putBoolean(INSTALLED, value).apply()
    }
}

// Hidden games (the Library's Hidden tab, including games hidden in Steam) stay off the shelf in both modes.
internal fun shelfEntries(
    all: List<LibraryEntry>, favorites: Set<String>, installedOnly: Boolean = false, hidden: (LibraryEntry) -> Boolean = { false },
): List<LibraryEntry> =
    all.filter { (if (installedOnly) it.installed else it.favoriteKey in favorites) && !hidden(it) }
        .distinctBy { it.favoriteKey }.sortedWith(compareBy({ shelfSortKey(it.title) }, { it.key }))

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
    remember { ShelfPrefs.load(ctx) }
    remember { EntryNames.load(ctx) }
    var renaming by remember { mutableStateOf<LibraryEntry?>(null) }
    var options by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<LibraryEntry?>(null) }
    var modeFor by remember { mutableStateOf<ModeTarget?>(null) }
    var artFor by remember { mutableStateOf<LibraryEntry?>(null) }
    remember { DockPrefs.load(ctx) }
    var dockMenu by remember { mutableStateOf<DockSlot?>(null) }
    var addingToDock by remember { mutableStateOf(false) }
    var iconFor by remember { mutableStateOf<HomeApp.LaunchableApp?>(null) }
    var popoutFor by remember { mutableStateOf<DockSlot?>(null) }
    var dockRenaming by remember { mutableStateOf<DockSlot?>(null) }
    var rowMenu by remember { mutableStateOf<Pair<DockSlot, DockSlot>?>(null) }
    LaunchedEffect(Unit) { StoresState.refresh(ctx) }
    LaunchedEffect(s.romsDir) { AndroidRoms.refresh(ctx, s.romsDir) }
    val signedIn = Store.entries.filter { StoresState.isSignedIn(it) }
    LaunchedEffect(signedIn) { signedIn.forEach { StoresState.loadLibrary(ctx, it) } }
    val owned = signedIn.associateWith { StoresState.library[it].orEmpty() }
    val installedStore = StoresState.installed.map { it.key }.toSet()
    val cloudRows = rememberXboxCloudRows(s)
    val favorites = LibraryFavorites.keys
    val installedOnly = ShelfPrefs.installedOnly
    val entryNames = EntryNames.names
    rememberHiddenSync()
    val hiddenKeys = LibraryHidden.keys
    val hiddenSteam = LibraryHidden.steam
    val androidReady = AndroidRoms.installed.filterValues { it.isNotEmpty() }.keys
    val entries = remember(s.steamGames, cloudRows, owned, installedStore, s.androidApps, HomeLibrary.added, HomeLibrary.removed, HomeLibrary.emulators, AndroidRoms.games, androidReady, s.emulators, favorites, installedOnly, entryNames, hiddenKeys, hiddenSteam) {
        shelfEntries(
            renamedEntries(
                libraryEntries(s.steamGames + cloudRows, owned, installedStore) +
                    androidEntries(s.androidApps, HomeLibrary.added, HomeLibrary.removed, HomeLibrary.emulators) + emulationEntries(AndroidRoms.games, androidReady, s.emulators),
                entryNames,
            ),
            favorites, installedOnly, LibraryHidden::isHidden,
        )
    }
    LaunchedEffect(entries.map { it.key + "|" + it.title }) {
        ShelfArt.fetchAll(ctx, entries.map { e ->
            ShelfArt.Wanted(e.key, e.title, e.game?.takeIf { com.droiddeck.launcher.frontend.Library.fromSteamClient(it) }?.appId)
        })
    }
    val open: (LibraryEntry) -> Unit = { e ->
        ShelfState.lastFocused = e.key
        val app = e.app
        val rom = e.rom
        val deck = e.deck
        val game = e.game
        val item = e.item
        when {
            app != null -> a.onAndroidApp(app, null)
            deck != null -> a.onRom(deck)
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
    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind { drawRect(Color.Transparent, blendMode = BlendMode.Clear) }
            .onPreviewKeyEvent { event ->
                val start = event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_START
                if (start && event.type == KeyEventType.KeyUp) options = true
                start
            },
    ) {
        val dockHeight = 96.dp
        val gridHeight = maxHeight - dockHeight
        val tile = gridHeight / (SHELF_ROWS + (SHELF_ROWS + 1) * SHELF_GAP)
        val gap = tile * SHELF_GAP
        if (entries.isEmpty()) {
            Text(
                stringResource(if (installedOnly) R.string.home_shelf_empty_installed else R.string.home_shelf_empty), color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(bottom = dockHeight).padding(horizontal = 48.dp),
            )
        } else LazyHorizontalGrid(
            rows = GridCells.Fixed(SHELF_ROWS), state = grid,
            modifier = Modifier.fillMaxWidth().height(gridHeight),
            contentPadding = PaddingValues(horizontal = max(gap, 40.dp), vertical = gap),
            horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            itemsIndexed(entries, key = { _, e -> e.key }) { i, e ->
                ShelfTile(e, tile, if (i == start) Modifier.focusRequester(tileFocus) else Modifier, onMenu = { itemMenu = e }) { open(e) }
            }
        }
        ShelfDock(s, a, dockFocus, { options = true }, { dockMenu = it }, { parent, row -> rowMenu = parent to row }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(dockHeight))
    }
    if (options) ShelfMenu(
        stringResource(R.string.home_shelf_options),
        listOf(
            ShelfMenuRow(stringResource(R.string.home_shelf_favorites), checked = !installedOnly) { ShelfPrefs.setInstalledOnly(ctx, false); options = false },
            ShelfMenuRow(stringResource(R.string.home_shelf_installed), checked = installedOnly) { ShelfPrefs.setInstalledOnly(ctx, true); options = false },
            ShelfMenuRow(stringResource(R.string.home_dock_add)) { options = false; addingToDock = true },
        ),
    ) { options = false }
    dockMenu?.let { slot ->
        val shown: (String) -> Boolean = { dockResolvable(it, s.androidApps) }
        val visible = DockPrefs.items.filter(shown)
        val at = visible.indexOf(slot.id)
        ShelfMenu(
            slot.label,
            listOfNotNull(
                ShelfMenuRow(stringResource(R.string.home_dock_icon)) { dockMenu = null; iconFor = slot.iconApp },
                ShelfMenuRow(stringResource(R.string.home_dock_icon_default)) { HomeIcons.clear(ctx, slot.iconApp.key); dockMenu = null }
                    .takeIf { slot.iconApp.key in HomeIcons.overrides },
                ShelfMenuRow(stringResource(R.string.home_shelf_rename_item)) { dockMenu = null; dockRenaming = slot },
                ShelfMenuRow(stringResource(R.string.home_dock_popout)) { dockMenu = null; popoutFor = slot },
                ShelfMenuRow(stringResource(R.string.home_dock_move_left)) { DockPrefs.move(ctx, slot.id, -1, shown); dockMenu = null }.takeIf { at > 0 },
                ShelfMenuRow(stringResource(R.string.home_dock_move_right)) { DockPrefs.move(ctx, slot.id, 1, shown); dockMenu = null }.takeIf { at in 0 until visible.lastIndex },
                ShelfMenuRow(stringResource(R.string.home_dock_remove)) { DockPrefs.remove(ctx, slot.id); dockMenu = null }.takeIf { slot.id != DockPrefs.LIBRARY },
            ),
        ) { dockMenu = null }
    }
    rowMenu?.let { (parent, row) ->
        ShelfMenu(
            row.label,
            listOfNotNull(
                ShelfMenuRow(stringResource(R.string.home_dock_icon)) { rowMenu = null; iconFor = row.iconApp },
                ShelfMenuRow(stringResource(R.string.home_dock_icon_default)) { HomeIcons.clear(ctx, row.iconApp.key); rowMenu = null }
                    .takeIf { row.iconApp.key in HomeIcons.overrides },
                ShelfMenuRow(stringResource(R.string.home_shelf_rename_item)) { rowMenu = null; dockRenaming = row },
                ShelfMenuRow(stringResource(R.string.home_dock_popout_remove, parent.label)) { DockPrefs.removeFromPopout(ctx, parent.id, row.id); rowMenu = null },
            ),
        ) { rowMenu = null }
    }
    dockRenaming?.let { slot ->
        ShelfRenameDialog(slot.label, renamed = slot.id in DockPrefs.names, onSave = { name ->
            dockRenaming = null
            DockPrefs.setName(ctx, slot.id, name)
        }) { dockRenaming = null }
    }
    if (addingToDock) {
        val inDock = DockPrefs.items.toSet()
        val pages = DockPrefs.PAGES.filter { "page:$it" !in inDock && dockPageGlyph(it) != null }.map { page ->
            ShelfMenuRow(stringResource(dockPageLabel(page))) { DockPrefs.add(ctx, "page:$page"); addingToDock = false }
        }
        val apps = s.androidApps.filter { appDockId(it.packageName, it.className) !in inDock }.sortedBy { it.label.lowercase() }.map { app ->
            ShelfMenuRow(app.label) { DockPrefs.add(ctx, appDockId(app.packageName, app.className)); addingToDock = false }
        }
        ShelfMenu(stringResource(R.string.home_dock_add_title), pages + apps) { addingToDock = false }
    }
    iconFor?.let { app -> AppIconPicker(app, 24.dp) { iconFor = null } }
    popoutFor?.let { slot ->
        val chosen = DockPrefs.popouts[slot.id].orEmpty()
        val pages = DockPrefs.PAGES.filter { dockPageGlyph(it) != null }.map { "page:$it" }.filter { it != slot.id }.map { id ->
            ShelfMenuRow(stringResource(dockPageLabel(dockPage(id).orEmpty())), checked = id in chosen) { DockPrefs.togglePopout(ctx, slot.id, id) }
        }
        val apps = s.androidApps.filter { appDockId(it.packageName, it.className) != slot.id }.sortedBy { it.label.lowercase() }.map { app ->
            val id = appDockId(app.packageName, app.className)
            ShelfMenuRow(app.label, checked = id in chosen) { DockPrefs.togglePopout(ctx, slot.id, id) }
        }
        ShelfMenu(stringResource(R.string.home_dock_popout_title, slot.label), pages + apps, confirm = stringResource(R.string.home_dock_done)) { popoutFor = null }
    }
    itemMenu?.let { e ->
        val favorite = e.favoriteKey in LibraryFavorites.keys
        val mode = if (com.droiddeck.launcher.core.RedmagicPerformance.supported(ctx)) modeTarget(e, AndroidRoms.installed, AndroidRoms.gameChosen, AndroidRoms.chosen) else null
        ShelfMenu(
            e.title,
            listOfNotNull(
                ShelfMenuRow(stringResource(R.string.home_shelf_artwork)) { itemMenu = null; artFor = e },
                ShelfMenuRow(stringResource(R.string.home_shelf_rename_item)) { itemMenu = null; renaming = e },
                ShelfMenuRow(stringResource(if (favorite) R.string.fm_favorite_remove else R.string.fm_favorite_add)) { itemMenu = null; LibraryFavorites.toggle(ctx, e.favoriteKey) },
                mode?.let { t -> ShelfMenuRow(stringResource(R.string.redmagic_perf_title)) { itemMenu = null; modeFor = t } },
                ShelfMenuRow(stringResource(R.string.home_shelf_open_library)) { itemMenu = null; ShelfState.leave(if (e.app != null || e.rom != null || e.deck != null) "games" else e.key) },
            ),
        ) { itemMenu = null }
    }
    modeFor?.let { t -> RedmagicModeDialog(t, s.adbPaired) { modeFor = null } }
    artFor?.let { e -> ShelfArtChooser(e) { artFor = null } }
    renaming?.let { e ->
        ShelfRenameDialog(e.title, renamed = e.key in entryNames, onSave = { name ->
            renaming = null
            EntryNames.set(ctx, e.key, name)
            ShelfArt.renamed(ctx, e.key)
            com.droiddeck.launcher.frontend.LibraryArt.renamed(ctx, e.key)
        }) { renaming = null }
    }
}

@Composable
private fun ShelfRenameDialog(current: String, renamed: Boolean, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_shelf_rename)) },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    text, { text = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(field).focusGlide(),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSave(text) }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                )
                if (renamed) androidx.compose.material3.TextButton(onClick = { onSave(null) }, modifier = Modifier.focusGlide(androidx.compose.foundation.shape.CircleShape)) {
                    Text(stringResource(R.string.home_shelf_rename_reset))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.focusGlide(androidx.compose.foundation.shape.CircleShape)) {
                Text(stringResource(R.string.home_shelf_rename_save))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.focusGlide(androidx.compose.foundation.shape.CircleShape)) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun ShelfArtChooser(e: LibraryEntry, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val steamAppId = e.game?.takeIf { com.droiddeck.launcher.frontend.Library.fromSteamClient(it) }?.appId
    val target = remember(e.key) {
        ArtTarget(
            e.title,
            { c, _ -> ShelfArt.chosenRef(c, e.key) },
            { c, _ -> ShelfArt.choices(c, e.key, e.title, steamAppId) },
            { c, _, option -> ShelfArt.set(c, e.key, option) },
            { c, _ -> ShelfArt.reset(c, e.key) },
            { _ -> null },
            { _, _ -> false },
        )
    }
    val slot = com.droiddeck.launcher.frontend.AddedGameArt.Slot.ICON
    val file = remember(e.key, ShelfArt.version) { ShelfArt.chosen(ctx, e.key) }
    DisposableEffect(Unit) {
        ShelfArt.hold()
        onDispose { ShelfArt.release() }
    }
    ArtChooserDialog(
        target, slot, com.droiddeck.launcher.frontend.AddedGameArt.SlotState(slot, file, com.droiddeck.launcher.frontend.AddedGameArt.SGDB, file != null),
        onBack = onClose, onCloseAll = onClose,
    ) {}
}

internal class ShelfMenuRow(val label: String, val checked: Boolean? = null, val onClick: () -> Unit)

// One menu style for the whole shelf: the Shelf menu and every item menu.
@Composable
private fun ShelfMenu(title: String, rows: List<ShelfMenuRow>, confirm: String? = null, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                rows.forEachIndexed { i, row ->
                    androidx.compose.material3.TextButton(
                        onClick = row.onClick,
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                            .focusGlide(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                    ) {
                        Text(
                            when (row.checked) { true -> "\u2713  "; false -> "     "; null -> "" } + row.label,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.focusGlide(androidx.compose.foundation.shape.CircleShape)) {
                Text(confirm ?: stringResource(R.string.common_cancel))
            }
        },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ShelfTile(e: LibraryEntry, size: Dp, focus: Modifier, onMenu: () -> Unit, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "shelfTile")
    val cover = remember(e.key, e.app, e.rom, e.deck, e.game, e.item) { entryArt(ctx, e, tall = true) }
    val art = ShelfArt.square(ctx, e.key) ?: cover
    val shape = RoundedCornerShape(size * 0.12f)
    Box(
        Modifier.size(size).zIndex(if (focused) 1f else 0f).graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.border(size * 0.022f, SHELF_RING, shape) else Modifier)
            .clip(shape).background(Color(0xFF161616))
            .semantics { contentDescription = e.title }
            .then(focus)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = onMenu)
            .menuKey(onMenu)
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

private enum class DockGlyph { LIBRARY, STORES, APPS, SETTINGS, STEAM, TERMINAL, MENU }

// A page with no glyph is not shown or offered: Dev Harness follows its rail tab, so switching the tab off
// hides it from the dock and pop-outs without forgetting where it was.
private fun dockPageGlyph(page: String): DockGlyph? = when (page) {
    "games" -> DockGlyph.LIBRARY
    "stores" -> DockGlyph.STORES
    AppUiPrefs.HOME_PAGE_APPS -> DockGlyph.APPS
    "setup" -> DockGlyph.SETTINGS
    "steam" -> DockGlyph.STEAM
    DockPrefs.AGENT -> DockGlyph.TERMINAL.takeIf { com.droiddeck.launcher.agent.AgentTab.shown }
    else -> null
}

private fun dockPageLabel(page: String): Int = when (page) {
    "games" -> R.string.rail_games
    "stores" -> R.string.rail_stores
    AppUiPrefs.HOME_PAGE_APPS -> R.string.rail_apps
    "setup" -> R.string.rail_setup
    DockPrefs.AGENT -> R.string.rail_agent
    else -> R.string.rail_steam
}

private class DockSlot(val id: String, val label: String, val glyph: DockGlyph?, val app: HomeApp.LaunchableApp?, val icon: Bitmap?, val iconApp: HomeApp.LaunchableApp)

private fun dockResolvable(id: String, apps: List<HomeApp.LaunchableApp>): Boolean {
    val component = dockApp(id)
    return dockPage(id)?.let { dockPageGlyph(it) != null } ?: (component != null && apps.any { it.packageName == component.first && it.className == component.second })
}

@Composable
private fun rememberDockSlot(id: String, s: FrontEndState): DockSlot? {
    val page = dockPage(id)
    val component = dockApp(id)
    val name = DockPrefs.names[id]
    if (page != null) {
        val glyph = dockPageGlyph(page) ?: return null
        val label = stringResource(dockPageLabel(page))
        val iconApp = remember(page, label) { pageIconApp(page, label) }
        return DockSlot(id, name ?: label, glyph, null, rememberPageIcon(iconApp.key), iconApp)
    }
    if (component == null) return null
    val app = s.androidApps.firstOrNull { it.packageName == component.first && it.className == component.second } ?: return null
    return DockSlot(id, name ?: app.label, null, app, app.icon, app)
}

@Composable
private fun rememberPageIcon(key: String): Bitmap? {
    val ctx = LocalContext.current
    val choice = HomeIcons.overrides[key]
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.roundToPx() }
    val icon by androidx.compose.runtime.produceState<Bitmap?>(null, choice) {
        value = choice?.let { c ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.droiddeck.launcher.IconPack.drawable(ctx, c.pack, c.drawable, ctx.resources.displayMetrics.densityDpi)?.toBitmap(px, px) }.getOrNull()
            }
        }
    }
    return icon
}

@Composable
private fun ShelfDock(
    s: FrontEndState, a: FrontEndActions, first: FocusRequester, onOptions: () -> Unit, onMenu: (DockSlot) -> Unit,
    onRowMenu: (DockSlot, DockSlot) -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    val activate: (DockSlot) -> Unit = { slot ->
        val app = slot.app
        when {
            app != null -> a.onAndroidApp(app, null)
            slot.id == "page:steam" -> if (s.ready) a.onPlay() else ShelfState.leave("steam")
            else -> ShelfState.leave(dockPage(slot.id))
        }
    }
    val firstId = DockPrefs.items.firstOrNull { dockResolvable(it, s.androidApps) }
    val step = with(androidx.compose.ui.platform.LocalDensity.current) { (64.dp + DOCK_SPACING).toPx() }
    Box(modifier) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(DOCK_SPACING), verticalAlignment = Alignment.CenterVertically) {
            DockPrefs.items.forEach { id ->
                key(id) {
                    val slot = rememberDockSlot(id, s)
                    if (slot != null) {
                        val inside = DockPrefs.popouts[id].orEmpty()
                        val hasPopout = inside.any { dockResolvable(it, s.androidApps) }
                        DockItem(
                            slot.glyph, slot.icon, slot.label, if (id == firstId) Modifier.focusRequester(first) else Modifier,
                            step = step,
                            onMenu = { onMenu(slot) },
                            onPopout = if (!hasPopout) null else ({ open = if (open == id) null else id }),
                            onDragStep = { delta -> DockPrefs.move(ctx, id, delta) { dockResolvable(it, s.androidApps) } },
                            anchored = {
                                if (open == id && hasPopout) DockPopout(slot, inside, s,
                                    onHeader = { open = null; activate(slot) },
                                    onRow = { row -> open = null; activate(row) },
                                    onRowMenu = { row -> open = null; onRowMenu(slot, row) },
                                ) { open = null }
                            },
                        ) { activate(slot) }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.CenterStart).padding(start = 28.dp)) {
            DockItem(DockGlyph.MENU, null, stringResource(R.string.home_shelf_options), Modifier, step = step, onMenu = null, onPopout = null, onDragStep = null, onClick = onOptions)
        }
    }
}

private val DOCK_SPACING = 22.dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    glyph: DockGlyph?, icon: Bitmap?, label: String, focus: Modifier, step: Float,
    onMenu: (() -> Unit)?, onPopout: (() -> Unit)?, onDragStep: ((Int) -> Unit)?,
    anchored: @Composable () -> Unit = {}, onClick: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipe = with(density) { 40.dp.toPx() }
    val slop = with(density) { 12.dp.toPx() }
    var focused by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(0f) }
    val scale by animateFloatAsState(if (focused || drag != 0f) 1.1f else 1f, label = "shelfDock")
    val popout by androidx.compose.runtime.rememberUpdatedState(onPopout)
    val moved by androidx.compose.runtime.rememberUpdatedState(onDragStep)
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.size(64.dp).zIndex(if (drag != 0f) 2f else 0f).graphicsLayer { scaleX = scale; scaleY = scale; translationX = drag }
            .then(if (focused || drag != 0f) Modifier.background(Color.White.copy(alpha = 0.08f), shape).border(2.dp, SHELF_RING, shape) else Modifier)
            .clip(shape)
            .semantics { contentDescription = label }
            .then(focus)
            .onFocusChanged { focused = it.isFocused }
            // Sideways drags reorder the dock; an upward swipe opens the pop-out. Keyed on Unit so a reorder mid-drag does not restart the gesture.
            .then(if (onDragStep == null && onPopout == null) Modifier else Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var dy = 0f
                    var steps = 0
                    var mode = 0
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val d = change.positionChange()
                        dx += d.x
                        dy += d.y
                        if (mode == 0 && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                            mode = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) (if (moved != null) 1 else 3) else (if (popout != null) 2 else 3)
                        }
                        if (mode == 1) {
                            change.consume()
                            val rest = dx - steps * step
                            if (rest > step / 2) { steps++; moved?.invoke(1) } else if (rest < -step / 2) { steps--; moved?.invoke(-1) }
                            drag = dx - steps * step
                        } else if (mode == 2) change.consume()
                        if (!change.pressed) break
                    }
                    if (mode == 2 && dy < -swipe) popout?.invoke()
                    drag = 0f
                }
            })
            .then(if (onPopout == null) Modifier else Modifier.onPreviewKeyEvent { event ->
                val x = event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_X
                if (x && event.type == KeyEventType.KeyUp) onPopout()
                x
            })
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onMenu)
            .then(if (onMenu != null) Modifier.menuKey(onMenu) else Modifier)
            .controllerConfirm(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> androidx.compose.foundation.Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
            glyph != null -> Box(Modifier.size(36.dp).drawBehind { drawGlyph(glyph, if (glyph == DockGlyph.LIBRARY) Color(0xFF3B82F6) else Color(0xFFEDEDED)) })
        }
        if (onPopout != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(4.dp).background(Color(0xFFEDEDED), androidx.compose.foundation.shape.CircleShape))
        anchored()
    }
}

private class AboveAnchor(private val gap: Int, private val margin: Int) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin, maxX)
        val y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(margin)
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

// Niagara-style: a card above the dock item; its header opens the item itself.
@Composable
private fun DockPopout(
    slot: DockSlot, inside: List<String>, s: FrontEndState,
    onHeader: () -> Unit, onRow: (DockSlot) -> Unit, onRowMenu: (DockSlot) -> Unit, onDismiss: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val provider = remember(density) { with(density) { AboveAnchor(12.dp.roundToPx(), 16.dp.roundToPx()) } }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = provider, onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.width(340.dp).heightIn(max = 420.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF161616))
                .onPreviewKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    val close = code == android.view.KeyEvent.KEYCODE_BUTTON_B || code == android.view.KeyEvent.KEYCODE_BACK || code == android.view.KeyEvent.KEYCODE_BUTTON_X
                    if (close && event.type == KeyEventType.KeyUp) onDismiss()
                    close
                }
                .verticalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 6.dp),
        ) {
            PopoutRow(slot.icon, slot.glyph, slot.label, header = true, focus = Modifier.focusRequester(first), onMenu = null, onClick = onHeader)
            inside.forEach { id ->
                key(id) {
                    val row = rememberDockSlot(id, s)
                    if (row != null) PopoutRow(row.icon, row.glyph, row.label, header = false, focus = Modifier, onMenu = { onRowMenu(row) }) { onRow(row) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PopoutRow(icon: Bitmap?, glyph: DockGlyph?, label: String, header: Boolean, focus: Modifier, onMenu: (() -> Unit)?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)
            .then(if (focused) Modifier.background(Color.White.copy(alpha = 0.08f), shape).border(2.dp, SHELF_RING, shape) else Modifier)
            .clip(shape).then(focus)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onMenu)
            .then(if (onMenu != null) Modifier.menuKey(onMenu) else Modifier)
            .controllerConfirm(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (header) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val size = if (header) 28.dp else 40.dp
        when {
            icon != null -> androidx.compose.foundation.Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
            glyph != null -> Box(Modifier.size(size).drawBehind { drawGlyph(glyph, Color(0xFFEDEDED)) })
            else -> Box(Modifier.size(size))
        }
        Text(label, color = Color.White, fontSize = if (header) 17.sp else 18.sp, fontWeight = if (header) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        if (header) Text("\u2197", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
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
        DockGlyph.MENU -> {
            drawRoundRect(color, p(7f, 9f), Size(34f * u, 8f * u), CornerRadius(4f * u), style = stroke)
            drawRoundRect(color, p(7f, 20f), Size(34f * u, 8f * u), CornerRadius(4f * u), style = stroke)
            drawRoundRect(color, p(7f, 31f), Size(34f * u, 8f * u), CornerRadius(4f * u), style = stroke)
        }
        DockGlyph.TERMINAL -> {
            drawRoundRect(color, p(6f, 9f), Size(36f * u, 30f * u), CornerRadius(6f * u), style = stroke)
            line(13f, 18f, 19f, 24f); line(19f, 24f, 13f, 30f)
            line(23f, 31f, 33f, 31f)
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
