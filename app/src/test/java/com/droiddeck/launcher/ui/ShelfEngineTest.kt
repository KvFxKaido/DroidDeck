package com.droiddeck.launcher.ui

import com.droiddeck.launcher.HomeApp
import com.droiddeck.launcher.frontend.AndroidEmulators
import com.droiddeck.launcher.frontend.Library
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ShelfEngineTest {
    private val ps2 = AndroidEmulators.systems.first { it.id == "ps2" }
    private fun installed(pkg: String, label: String) = AndroidEmulators.Installed(AndroidEmulators.recipes.first { it.system == "ps2" }, pkg, label)
    private fun engine(e: LibraryEntry, installed: Map<String, List<AndroidEmulators.Installed>> = emptyMap(), gameChosen: Map<String, String> = emptyMap(), deck: Map<String, String> = emptyMap()) =
        shelfEngine(e, installed, gameChosen, emptyMap(), deck)?.let { it.kind to it.name }

    private fun steam(prefix: File?, library: String = "/steam") =
        LibraryEntry("steam:1", Library.SOURCE_STEAM, "Game", Library.SteamGame(1, "Game", null, library, protonPrefix = prefix), null, installed = true)

    @Test fun anAndroidAppRunsOnAndroid() {
        val app = HomeApp.LaunchableApp("Lost Crown", "com.ubisoft.poplc", "Main", null)
        val e = LibraryEntry("android:com.ubisoft.poplc", Library.SOURCE_ANDROID, "Lost Crown", null, null, installed = true, app = app)
        assertEquals(EngineKind.ANDROID to null, engine(e))
    }

    @Test fun aRomNamesTheEmulatorItWouldOpenIn() {
        val game = AndroidEmulators.Game(ps2, "GT4", File("/roms/ps2/gt4.iso"))
        val e = romEntries(listOf(game))[0]
        val a = installed("xyz.aethersx2.android", "AetherSX2")
        val b = installed("com.other.ps2", "Other")
        assertEquals(EngineKind.EMULATOR to "Other", engine(e, mapOf("ps2" to listOf(a, b)), mapOf(game.file.path to "com.other.ps2")))
        assertEquals(EngineKind.EMULATOR to "AetherSX2", engine(e, mapOf("ps2" to listOf(a))))
    }

    @Test fun aRomWithNoEmulatorChosenYetShowsItsSystem() {
        val e = romEntries(listOf(AndroidEmulators.Game(ps2, "GT4", File("/roms/ps2/gt4.iso"))))[0]
        val two = listOf(installed("xyz.aethersx2.android", "AetherSX2"), installed("com.other.ps2", "Other"))
        assertEquals(EngineKind.EMULATOR to "PS2", engine(e, mapOf("ps2" to two)))
    }

    @Test fun aDroidDeckEmulatorGameNamesItsEmulator() {
        val rom = Library.Rom("WipEout HD", File("/roms/ps3/w"), "/root/ROMs/w", "rpcs3")
        val e = emulationEntries(emptyList(), emptySet(), listOf(Library.Emulator("rpcs3", "RPCS3", "ps3", "/opt/rpcs3", true, listOf(rom))))[0]
        assertEquals(EngineKind.EMULATOR to "RPCS3", engine(e, deck = mapOf("rpcs3" to "RPCS3")))
    }

    @Test fun aPcGameSaysProtonOnlyOnceItHasAProtonPrefix() {
        assertEquals(EngineKind.PROTON to null, engine(steam(File("/compatdata/1/pfx"))))
        assertEquals(EngineKind.PC to null, engine(steam(null)))
    }

    @Test fun anXboxCloudGameStreams() {
        assertEquals(EngineKind.CLOUD to null, engine(steam(null, Library.XBOX_CLOUD)))
    }
}
