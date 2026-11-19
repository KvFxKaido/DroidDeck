package com.droiddeck.launcher.ui

import com.droiddeck.launcher.frontend.AndroidEmulators
import com.droiddeck.launcher.frontend.Library
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class EmulationEntriesTest {
    private val ps2 = AndroidEmulators.systems.first { it.id == "ps2" }
    private fun android(path: String, title: String) = AndroidEmulators.Game(ps2, title, File(path))
    private fun rom(path: String, title: String, emu: String) = Library.Rom(title, File(path), "/root/ROMs/x", emu)
    private fun emulator(id: String, installed: Boolean, vararg games: Library.Rom) =
        Library.Emulator(id, id, "sys", "/opt/$id", installed, games.toList())

    @Test fun rpcs3GamesJoinTheEmulationSourceAndOpenInDroidDeck() {
        val wipeout = rom("/roms/ps3/Wipeout HD", "WipEout HD", "rpcs3")
        val entries = emulationEntries(emptyList(), emptySet(), listOf(emulator("rpcs3", true, wipeout)))
        assertEquals(1, entries.size)
        assertEquals(Library.SOURCE_EMULATION, entries[0].source)
        assertEquals("WipEout HD", entries[0].title)
        assertEquals(wipeout, entries[0].deck)
        assertEquals("deckrom:rpcs3:" + File("/roms/ps3/Wipeout HD").path, entries[0].key)
    }

    @Test fun anEmulatorThatIsNotInstalledAddsNothing() {
        assertEquals(0, emulationEntries(emptyList(), emptySet(), listOf(emulator("rpcs3", false, rom("/roms/ps3/a", "A", "rpcs3")))).size)
    }

    @Test fun aFileBothSidesFindIsListedOnceAsAndroidWhenAnAndroidEmulatorIsReady() {
        val entries = emulationEntries(
            listOf(android("/roms/ps2/gt4.iso", "GT4")), setOf("ps2"),
            listOf(emulator("armsx2", true, rom("/roms/ps2/gt4.iso", "GT4", "armsx2"))),
        )
        assertEquals(1, entries.size)
        assertEquals(true, entries[0].rom != null)
    }

    @Test fun aFileBothSidesFindIsListedOnceAsDroidDeckWhenNoAndroidEmulatorIsReady() {
        val entries = emulationEntries(
            listOf(android("/roms/ps2/gt4.iso", "GT4"), android("/roms/ps2/only-android.iso", "Other")), emptySet(),
            listOf(emulator("armsx2", true, rom("/roms/ps2/gt4.iso", "GT4", "armsx2"))),
        )
        assertEquals(2, entries.size)
        assertEquals(true, entries.first { it.title == "GT4" }.deck != null)
        assertEquals(true, entries.first { it.title == "Other" }.rom != null)
    }

    @Test fun aRenameKeepsTheDroidDeckGame() {
        val e = emulationEntries(emptyList(), emptySet(), listOf(emulator("rpcs3", true, rom("/roms/ps3/w", "W", "rpcs3"))))
        val renamed = renamedEntries(e, mapOf(e[0].key to "Wipeout"))
        assertEquals("Wipeout", renamed[0].title)
        assertEquals(e[0].deck, renamed[0].deck)
    }
}
