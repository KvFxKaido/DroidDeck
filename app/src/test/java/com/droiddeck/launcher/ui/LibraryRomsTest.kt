package com.droiddeck.launcher.ui

import com.droiddeck.launcher.frontend.AndroidEmulators
import com.droiddeck.launcher.frontend.Library
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRomsTest {
    private val ps2 = AndroidEmulators.systems.first { it.id == "ps2" }
    private val game = AndroidEmulators.Game(ps2, "Okami", File("/storage/emulated/0/Emulators/PS2/Games/Okami.iso"), modified = 5_000)
    private val steam = LibraryEntry("app:440", Library.SOURCE_STEAM, "Team Fortress 2", Library.SteamGame(440, "Team Fortress 2", null, "Steam library"), null, true)

    @Test fun aRomIsAnInstalledEntryThatStaysOutOfTheDetailAndDesktopRoutes() {
        val entry = romEntries(listOf(game)).single()
        assertEquals(Library.SOURCE_EMULATION, entry.source)
        assertEquals("Okami", entry.title)
        assertTrue(entry.installed)
        assertTrue(entry.key.startsWith(AndroidEmulators.KEY))
        for (prefix in listOf("app:", "rom:", "emu:", "sound:")) assertFalse(entry.key.startsWith(prefix))
        assertNull(entry.storeKey)
        assertEquals(entry.key, entry.favoriteKey)
    }

    @Test fun emulationGetsItsOwnSourceAfterAndroid() {
        val entries = listOf(steam) + romEntries(listOf(game))
        assertEquals(listOf("steam", "emulation"), librarySources(entries, emptyList()))
        assertTrue(inLibrarySource(Library.SOURCE_EMULATION, entries[1], emptySet()))
        assertFalse(inLibrarySource(Library.SOURCE_ANDROID, entries[1], emptySet()))
    }
}
