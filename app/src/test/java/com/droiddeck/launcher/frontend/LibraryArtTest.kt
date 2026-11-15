package com.droiddeck.launcher.frontend

import com.droiddeck.launcher.ui.libraryArtSlot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArtTest {
    private val cover = File("cover.jpg")
    private val wide = File("wide.jpg")

    @Test fun eachEntryGetsAStableFolderNameThatIsSafeAsAPath() {
        val rom = LibraryArt.folder("androidrom:/storage/emulated/0/Emulators/PS2/Games/Okami (USA).iso")
        assertEquals(rom, LibraryArt.folder("androidrom:/storage/emulated/0/Emulators/PS2/Games/Okami (USA).iso"))
        assertNotEquals(rom, LibraryArt.folder("android:a.game/a.game.Main"))
        assertTrue(Regex("[0-9a-f]{20}").matches(rom))
    }

    @Test fun aTileShowsTheArtForItsShapeAndFallsBackToTheOtherOne() {
        assertEquals(cover, LibraryArt.prefer(tall = true, cover = cover, wide = wide))
        assertEquals(wide, LibraryArt.prefer(tall = false, cover = cover, wide = wide))
        assertEquals(wide, LibraryArt.prefer(tall = true, cover = null, wide = wide))
        assertEquals(cover, LibraryArt.prefer(tall = false, cover = cover, wide = null))
        assertNull(LibraryArt.prefer(tall = true, cover = null, wide = null))
    }

    @Test fun theChooserEditsTheArtForTheViewInUse() {
        assertEquals(AddedGameArt.Slot.COVER, libraryArtSlot("capsules"))
        assertEquals(AddedGameArt.Slot.WIDE, libraryArtSlot("grid"))
        assertEquals(AddedGameArt.Slot.WIDE, libraryArtSlot("list"))
    }
}
