package com.droiddeck.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.droiddeck.launcher.frontend.AddedGameArt
import com.droiddeck.launcher.frontend.LibraryArt

internal class LibraryArtRequest(val key: String, val title: String)

internal fun libraryArtSlot(view: String): AddedGameArt.Slot = if (view == "capsules") AddedGameArt.Slot.COVER else AddedGameArt.Slot.WIDE

internal fun libraryArtTarget(key: String, title: String) = ArtTarget(
    title,
    { c, slot -> LibraryArt.chosenRef(c, key, slot) },
    { c, slot -> LibraryArt.choices(c, key, title, slot) },
    { c, slot, option -> LibraryArt.set(c, key, slot, option) },
    { c, slot -> LibraryArt.reset(c, key, slot) },
    { _ -> null },
    { _, _ -> false },
)

@Composable
internal fun LibraryArtChooser(request: LibraryArtRequest, view: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val slot = libraryArtSlot(view)
    val target = remember(request.key) { libraryArtTarget(request.key, request.title) }
    val file = LibraryArt.chosen(ctx, request.key, slot)
    ArtChooserDialog(target, slot, AddedGameArt.SlotState(slot, file, AddedGameArt.SGDB, file != null), onBack = onClose, onCloseAll = onClose) {}
}
