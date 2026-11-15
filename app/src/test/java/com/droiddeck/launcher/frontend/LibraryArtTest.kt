package com.droiddeck.launcher.frontend

import com.droiddeck.launcher.HomeApp
import com.droiddeck.launcher.ui.androidEntries
import com.droiddeck.launcher.ui.libraryArtSlot
import com.droiddeck.launcher.ui.libraryArtWanted
import com.droiddeck.launcher.ui.romEntries
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryArtTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cover = File("cover.jpg")
    private val wide = File("wide.jpg")
    private val day = 24L * 3600 * 1000

    private fun put(name: String, text: String = "image") = File(tmp.root, name).apply { writeText(text) }

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

    @Test fun artChosenByHandWinsOverFetchedArt() {
        val fetched = put("p.jpg")
        assertEquals(fetched, LibraryArt.shown(tmp.root, AddedGameArt.Slot.COVER))
        val picked = put("chosen-p-0000000000001.jpg")
        assertEquals(picked, LibraryArt.shown(tmp.root, AddedGameArt.Slot.COVER))
        assertNull(LibraryArt.shown(tmp.root, AddedGameArt.Slot.WIDE))
    }

    @Test fun resettingGoesBackToFetchedArtAndASecondResetTurnsFetchingOff() {
        val fetched = put("p.jpg")
        put("header.jpg")
        put("chosen-p-0000000000001.jpg")
        LibraryArt.resetIn(tmp.root, AddedGameArt.Slot.COVER)
        assertEquals(fetched, LibraryArt.shown(tmp.root, AddedGameArt.Slot.COVER))
        assertFalse(File(tmp.root, "auto-off").exists())
        LibraryArt.resetIn(tmp.root, AddedGameArt.Slot.COVER)
        assertNull(LibraryArt.shown(tmp.root, AddedGameArt.Slot.COVER))
        assertNull(LibraryArt.shown(tmp.root, AddedGameArt.Slot.WIDE))
        assertFalse(LibraryArt.needsFetch(tmp.root, now = System.currentTimeMillis(), keyChangedAt = 0))
    }

    @Test fun artIsFetchedOnceAndAMissIsRetriedAfterAWeekOrANewKey() {
        val now = System.currentTimeMillis()
        assertTrue(LibraryArt.needsFetch(tmp.root, now, keyChangedAt = 0))
        val tried = put("auto-tried", "")
        assertFalse(LibraryArt.needsFetch(tmp.root, now, keyChangedAt = 0))
        assertTrue(LibraryArt.needsFetch(tmp.root, now + 8 * day, keyChangedAt = 0))
        assertTrue(LibraryArt.needsFetch(tmp.root, now, keyChangedAt = tried.lastModified() + 1000))
        tried.delete()
        put("p.jpg")
        assertTrue(LibraryArt.needsFetch(tmp.root, now, keyChangedAt = 0))
        put("header.jpg")
        assertFalse(LibraryArt.needsFetch(tmp.root, now, keyChangedAt = 0))
    }

    @Test fun androidGamesAlwaysWantArtAndRomsOnlyOnceTheirOwnCoversHaveBeenTried() {
        val app = HomeApp.LaunchableApp("Game", "a.game", "a.game.Main", null, game = true)
        val ps2 = AndroidEmulators.systems.first { it.id == "ps2" }
        val bare = AndroidEmulators.Game(ps2, "Okami", File("/roms/PS2/Okami.iso"))
        val covered = AndroidEmulators.Game(ps2, "Ico", File("/roms/PS2/Ico.iso"), art = File("ico.img"))
        val entries = androidEntries(listOf(app), emptySet(), emptySet()) + romEntries(listOf(bare, covered))
        assertEquals(listOf("Game"), libraryArtWanted(entries, romCoversSettled = false).map { it.title })
        assertEquals(listOf("Game", "Okami"), libraryArtWanted(entries, romCoversSettled = true).map { it.title })
    }
}
