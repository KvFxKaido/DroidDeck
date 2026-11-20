package com.droiddeck.launcher.ui

import com.droiddeck.launcher.frontend.AndroidEmulators
import com.droiddeck.launcher.frontend.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ModeTargetTest {
    private val ps2 = AndroidEmulators.systems.first { it.id == "ps2" }
    private fun installed(pkg: String, label: String) = AndroidEmulators.Installed(AndroidEmulators.recipes.first { it.system == "ps2" }, pkg, label)

    @Test fun aDroidDeckEmulatorGameIsSetInItsOwnSessionByItsLibraryKey() {
        val rom = Library.Rom("WipEout HD", File("/roms/ps3/w"), "/root/ROMs/w", "rpcs3")
        val e = emulationEntries(emptyList(), emptySet(), listOf(Library.Emulator("rpcs3", "RPCS3", "ps3", "/opt/rpcs3", true, listOf(rom))))[0]
        assertEquals(ModeTarget.Session(deckRomKey("rpcs3", rom)), modeTarget(e, emptyMap(), emptyMap(), emptyMap()))
        assertEquals(e.key, deckRomKey("rpcs3", rom))
    }

    @Test fun aRomSetsTheModeOfTheEmulatorItWouldOpenIn() {
        val game = AndroidEmulators.Game(ps2, "GT4", File("/roms/ps2/gt4.iso"))
        val e = romEntries(listOf(game))[0]
        val a = installed("xyz.aethersx2.android", "AetherSX2")
        val b = installed("com.other.ps2", "Other")
        assertEquals(
            ModeTarget.App("com.other.ps2", "Other", forRom = "GT4"),
            modeTarget(e, mapOf("ps2" to listOf(a, b)), mapOf(game.file.path to "com.other.ps2"), emptyMap()),
        )
        assertNull(modeTarget(e, emptyMap(), emptyMap(), emptyMap()))
    }

    @Test fun aSteamGameHasNoEntryMenuMode() {
        assertNull(modeTarget(LibraryEntry("steam:1", Library.SOURCE_STEAM, "Game", null, null, installed = true), emptyMap(), emptyMap(), emptyMap()))
    }
}
