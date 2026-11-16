package com.droiddeck.launcher.frontend

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidEmulatorsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(path: String, text: String = "x"): File =
        File(tmp.root, path).apply { parentFile?.mkdirs(); writeText(text) }

    private fun found() = AndroidEmulators.scan(tmp.root).map { it.system.id + ":" + it.file.relativeTo(tmp.root).path.replace('\\', '/') }

    private fun target(system: String, name: String, pkg: String? = null): AndroidEmulators.Installed {
        val recipe = AndroidEmulators.recipes.first { it.system == system && it.name == name && (pkg == null || it.packageName == pkg) }
        return AndroidEmulators.Installed(recipe, pkg ?: recipe.packageName ?: "some.package", name)
    }

    private val link = "content://com.android.externalstorage.documents/tree/primary%3AEmulators/document/primary%3AEmulators%2FPS2%2FGames%2FGame.iso"
    private val path = "/storage/emulated/0/Emulators/PS2/Games/Game.iso"

    @Test fun gamesAreFoundUnderCapitalisedSystemFoldersAtTheDepthsTheyAreKept() {
        file("PSP/GAME/Lumines (USA).iso")
        file("PS2/Games/Okami.iso")
        file("PS2/Games/Sub/Ico.chd")
        file("GameCube/Wind Waker.rvz")
        file("Wii/Games/Galaxy.rvz")
        file("Wii U/Games/Splatoon.wua")
        file("Switch/Games/Celeste.nsp")
        file("Music/song.iso")
        assertEquals(
            listOf("gc:GameCube/Wind Waker.rvz", "ps2:PS2/Games/Okami.iso", "ps2:PS2/Games/Sub/Ico.chd", "psp:PSP/GAME/Lumines (USA).iso",
                "switch:Switch/Games/Celeste.nsp", "wii:Wii/Games/Galaxy.rvz", "wiiu:Wii U/Games/Splatoon.wua"),
            found(),
        )
        assertEquals("Lumines", AndroidEmulators.scan(tmp.root).first { it.system.id == "psp" }.title)
    }

    @Test fun emulatorDataFoldersAndBareBinFilesAreNotGames() {
        file("PS2/Bios/scph39001.bin")
        file("PS2/cache/shader.bin")
        file("PS2/Games/Okami.iso")
        file("PSP/SAVEDATA/ULUS10000/DATA.BIN")
        file("PSP/PPSSPP_STATE/Lumines.iso")
        file("Switch/Updates/Celeste [UPD].nsp")
        assertEquals(listOf("ps2:PS2/Games/Okami.iso"), found())
    }

    @Test fun aDiscSheetIsListedOnceAndItsTracksAreNot() {
        file("Dreamcast/Shenmue/Shenmue.gdi", "3\n1 0 4 2352 track01.bin 0\n2 756 0 2352 \"track02.raw\" 0\n3 45000 4 2352 track03.bin 0\n")
        file("Dreamcast/Shenmue/track01.bin")
        file("Dreamcast/Shenmue/track02.raw")
        file("Dreamcast/Shenmue/track03.bin")
        assertEquals(listOf("dreamcast:Dreamcast/Shenmue/Shenmue.gdi"), found())
    }

    @Test fun aPs2CdGameOpensItsFirstTrackAndIsNamedAfterItsSheet() {
        file("PS2/Games/Gradius V (USA).cue", "FILE \"Gradius V (USA).bin\" BINARY\n  TRACK 01 MODE2/2352\n")
        file("PS2/Games/Gradius V (USA).bin")
        val game = AndroidEmulators.scan(tmp.root).single()
        assertEquals("Gradius V (USA).bin", game.file.name)
        assertEquals("Gradius V", game.title)
    }

    @Test fun ps1DiscsAreListedByTheirSheetOrPlaylistAndNeverByTrack() {
        file("PS1/bios/scph1001.bin")
        file("PS1/Games/Crash/Crash.cue", "FILE \"Crash (Track 1).bin\" BINARY\n  TRACK 01 MODE2/2352\nFILE \"Crash (Track 2).bin\" BINARY\n  TRACK 02 AUDIO\n")
        file("PS1/Games/Crash/Crash (Track 1).bin")
        file("PS1/Games/Crash/Crash (Track 2).bin")
        file("PS1/Games/FF7/FF7.m3u", "FF7 (Disc 1).cue\nFF7 (Disc 2).cue\n")
        file("PS1/Games/FF7/FF7 (Disc 1).cue", "FILE \"FF7 (Disc 1).bin\" BINARY\n")
        file("PS1/Games/FF7/FF7 (Disc 2).cue", "FILE \"FF7 (Disc 2).bin\" BINARY\n")
        file("PS1/Games/FF7/FF7 (Disc 1).bin")
        file("PS1/Games/FF7/FF7 (Disc 2).bin")
        assertEquals(listOf("ps1:PS1/Games/Crash/Crash.cue", "ps1:PS1/Games/FF7/FF7.m3u"), found())
    }

    @Test fun duckStationGetsTheDiscInBootPath() {
        val launch = AndroidEmulators.launch(target("ps1", "DuckStation"), link, path)
        assertEquals("com.github.stenzek.duckstation", launch.packageName)
        assertEquals("com.github.stenzek.duckstation.EmulationActivity", launch.activity)
        assertEquals(AndroidEmulators.ACTION_MAIN, launch.action)
        assertNull(launch.data)
        assertEquals(mapOf("bootPath" to link), launch.extras)
    }

    @Test fun aGameChoiceBeatsTheSystemChoiceAndFallsBackWhenItsEmulatorIsGone() {
        val a = target("ps2", "AetherSX2", "xyz.aethersx2.android")
        val b = target("ps2", "AetherSX2", "xyz.aethersx2.tturnip")
        val both = listOf(a, b)
        assertEquals(b, AndroidEmulators.choose(both, forGame = b.packageName, forSystem = a.packageName))
        assertEquals(a, AndroidEmulators.choose(both, forGame = null, forSystem = a.packageName))
        assertEquals(a, AndroidEmulators.choose(both, forGame = "uninstalled.package", forSystem = a.packageName))
        assertNull(AndroidEmulators.choose(both, forGame = null, forSystem = null))
        assertEquals(a, AndroidEmulators.choose(listOf(a), forGame = b.packageName, forSystem = null))
    }

    @Test fun aSharedStoragePathBecomesADocumentUnderTheGrantedFolder() {
        assertEquals("primary:Emulators/PS2/Games/Game.iso", AndroidEmulators.documentId(path))
        assertEquals("primary:Emulators", AndroidEmulators.documentId("/sdcard/Emulators/"))
        assertEquals("primary:", AndroidEmulators.documentId("/storage/emulated/0"))
        assertEquals("1A2B-3C4D:Roms/x.iso", AndroidEmulators.documentId("/storage/1A2B-3C4D/Roms/x.iso"))
        assertNull(AndroidEmulators.documentId("/data/user/0/app/files/x.iso"))
        assertEquals(link, AndroidEmulators.documentUri("primary:Emulators", "primary:Emulators/PS2/Games/Game.iso"))
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3AEmulators/document/primary%3AEmulators%2FWii%20U%2FGames%2FA%20(USA).wua",
            AndroidEmulators.documentUri("primary:Emulators", "primary:Emulators/Wii U/Games/A (USA).wua"),
        )
    }

    @Test fun aGrantCoversOnlyItsOwnFolder() {
        assertTrue(AndroidEmulators.covers("primary:Emulators", "primary:Emulators/PS2/x.iso"))
        assertTrue(AndroidEmulators.covers("primary:", "primary:Emulators/PS2/x.iso"))
        assertFalse(AndroidEmulators.covers("primary:Emulators", "primary:EmulatorsOld/PS2/x.iso"))
        assertFalse(AndroidEmulators.covers("primary:Emulators/PS2", "primary:Emulators/PSP/x.iso"))
        assertFalse(AndroidEmulators.covers("1A2B-3C4D:", "primary:Emulators/x.iso"))
    }

    @Test fun ppssppRedreamAndCemuGetTheGameAsIntentData() {
        for ((system, name) in listOf("psp" to "PPSSPP", "dreamcast" to "redream", "wiiu" to "Cemu")) {
            val launch = AndroidEmulators.launch(target(system, name), link, path)
            assertEquals(AndroidEmulators.ACTION_VIEW, launch.action)
            assertEquals(link, launch.data)
            assertTrue(launch.extras.isEmpty())
        }
        assertEquals("org.ppsspp.ppsspp.PpssppActivity", AndroidEmulators.launch(target("psp", "PPSSPP Gold"), link, path).activity)
    }

    @Test fun aetherSx2DolphinAndKenjiGetTheGameInAnExtraAndNoData() {
        val ps2 = AndroidEmulators.launch(target("ps2", "AetherSX2", "xyz.aethersx2.tturnip"), link, path)
        assertEquals("xyz.aethersx2.tturnip", ps2.packageName)
        assertEquals("xyz.aethersx2.android.EmulationActivity", ps2.activity)
        assertEquals(AndroidEmulators.ACTION_MAIN, ps2.action)
        assertNull(ps2.data)
        assertEquals(mapOf("bootPath" to link), ps2.extras)
        val dolphin = AndroidEmulators.launch(target("gc", "Dolphin"), link, path)
        assertEquals("org.dolphinemu.dolphinemu.ui.main.MainActivity", dolphin.activity)
        assertEquals(mapOf("AutoStartFile" to link), dolphin.extras)
        val kenji = AndroidEmulators.launch(target("switch", "Kenji-NX"), link, path)
        assertEquals("org.kenjinx.android.LAUNCH_GAME", kenji.action)
        assertEquals(mapOf("bootPath" to link), kenji.extras)
    }

    @Test fun hakuXGetsTheGameAsIntentDataAndXboxImagesLoseTheirXisoSuffix() {
        val hakux = AndroidEmulators.launch(target("xbox", "hakuX"), link, path)
        assertEquals("com.rfandango.haku_x.LauncherActivity", hakux.activity)
        assertEquals(AndroidEmulators.ACTION_VIEW, hakux.action)
        assertEquals(link, hakux.data)
        assertTrue(hakux.extras.isEmpty())
        file("Xbox/Games/Halo (USA).xiso.iso")
        file("Xbox 360/Halo 3.iso")
        val games = AndroidEmulators.scan(tmp.root)
        assertEquals(listOf("xbox:Halo"), games.map { it.system.id + ":" + it.title })
    }

    @Test fun vitaGamesAreInstalledFoldersNamedByTheirParamSfoAndOpenedByTitleId() {
        fun sfo(title: String): ByteArray {
            val key = "TITLE\u0000".toByteArray()
            val value = (title + "\u0000").toByteArray(Charsets.UTF_8)
            val out = java.nio.ByteBuffer.allocate(36 + key.size + value.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            out.putInt(0x46535000).putInt(0x101).putInt(36).putInt(36 + key.size).putInt(1)
            out.putShort(0).putShort(0x204).putInt(value.size).putInt(value.size).putInt(0)
            return out.put(key).put(value).array()
        }
        File(tmp.root, "Vita/ux0/app/PCSA00015/sce_sys").mkdirs()
        File(tmp.root, "Vita/ux0/app/PCSA00015/sce_sys/param.sfo").writeBytes(sfo("WipEout\u00AE 2048"))
        File(tmp.root, "Vita/ux0/app/NOSFO0000").mkdirs()
        file("Vita/Games/loose.iso")
        val game = AndroidEmulators.scan(tmp.root).single()
        assertEquals("vita", game.system.id)
        assertEquals("WipEout 2048", game.title)
        val launch = AndroidEmulators.launch(target("vita", "Vita3K"), link, game.file.path)
        assertEquals("org.vita3k.emulator.Emulator", launch.activity)
        assertNull(launch.data)
        assertEquals(mapOf("title_id" to "PCSA00015"), launch.extras)
    }

    @Test fun aRecipeMarkedAsPathSendsThePlainPath() {
        val recipe = AndroidEmulators.Recipe("ps2", "X", "x.y", "x.y.Main", AndroidEmulators.ACTION_MAIN, extra = "bootPath", extraIsPath = true)
        assertEquals(mapOf("bootPath" to path), AndroidEmulators.launch(AndroidEmulators.Installed(recipe, "x.y", "X"), link, path).extras)
    }

    @Test fun edenIsFoundByItsActivityUnderWhateverPackageItShipsAs() {
        val eden = "org.yuzu.yuzu_emu.activities.EmulationActivity"
        val has = { pkg: String, cls: String ->
            when {
                pkg == "renamed.build" && cls == eden -> "Eden Nightly"
                pkg == "xyz.aethersx2.android" && cls == "xyz.aethersx2.android.EmulationActivity" -> "AetherSX2"
                pkg == "xyz.aethersx2.tturnip" && cls == "xyz.aethersx2.android.EmulationActivity" -> "NetherSX2"
                else -> null
            }
        }
        val installed = AndroidEmulators.installed(has) { listOf("other.app", "renamed.build") }
        assertEquals(listOf("ps2", "switch"), installed.keys.toList())
        assertEquals(listOf("AetherSX2", "NetherSX2"), installed.getValue("ps2").map { it.label })
        assertEquals("renamed.build", installed.getValue("switch").single().packageName)
        assertEquals("android.nfc.action.TECH_DISCOVERED", installed.getValue("switch").single().recipe.action)
    }

    @Test fun oneEmulatorIsUsedWithoutAskingAndSeveralNeedAChoice() {
        val a = target("ps2", "AetherSX2", "xyz.aethersx2.android")
        val b = target("ps2", "AetherSX2", "xyz.aethersx2.tturnip")
        assertEquals(a, AndroidEmulators.pick(listOf(a), null))
        assertNull(AndroidEmulators.pick(listOf(a, b), null))
        assertEquals(b, AndroidEmulators.pick(listOf(a, b), "xyz.aethersx2.tturnip"))
        assertNull(AndroidEmulators.pick(listOf(a, b), "uninstalled.package"))
        assertNull(AndroidEmulators.pick(emptyList(), null))
    }
}
