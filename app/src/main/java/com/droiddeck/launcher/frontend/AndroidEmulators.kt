package com.droiddeck.launcher.frontend

import java.io.File

object AndroidEmulators {
    const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val ACTION_VIEW = "android.intent.action.VIEW"

    class System(val id: String, val name: String, val folders: List<String>, val exts: Set<String>, val coverId: String? = null, val trackOfSheet: Boolean = false)

    class Recipe(
        val system: String, val name: String, val packageName: String?, val activity: String, val action: String,
        val data: Boolean = false, val extra: String? = null, val extraIsPath: Boolean = false,
    )

    class Installed(val recipe: Recipe, val packageName: String, val label: String)

    class Game(val system: System, val title: String, val file: File, val modified: Long = 0L, val art: File? = null) {
        val key get() = KEY + file.path
        fun withArt(art: File?) = Game(system, title, file, modified, art)
    }

    class Launch(val packageName: String, val activity: String, val action: String, val data: String?, val extras: Map<String, String>)

    const val KEY = "androidrom:"

    val systems = listOf(
        System("psp", "PSP", listOf("psp"), setOf("iso", "cso", "chd", "pbp"), coverId = "ppsspp"),
        System("ps2", "PS2", listOf("ps2"), setOf("iso", "chd", "cso", "gz"), coverId = "armsx2", trackOfSheet = true),
        System("gc", "GameCube", listOf("gc", "gamecube", "ngc"), setOf("iso", "rvz", "gcz", "ciso"), coverId = "dolphin"),
        System("wii", "Wii", listOf("wii"), setOf("iso", "rvz", "gcz", "wbfs", "ciso"), coverId = "dolphin"),
        System("wiiu", "Wii U", listOf("wiiu", "wii u"), setOf("wua", "wud", "wux"), coverId = "cemu"),
        System("dreamcast", "Dreamcast", listOf("dreamcast", "dc"), setOf("gdi", "cdi", "chd", "cue")),
        System("switch", "Switch", listOf("switch"), setOf("nsp", "xci")),
    )

    private const val DOLPHIN = "org.dolphinemu.dolphinemu"
    private const val AETHER = "xyz.aethersx2.android"

    val recipes = listOf(
        Recipe("psp", "PPSSPP", "org.ppsspp.ppsspp", "org.ppsspp.ppsspp.PpssppActivity", ACTION_VIEW, data = true),
        Recipe("psp", "PPSSPP Gold", "org.ppsspp.ppssppgold", "org.ppsspp.ppsspp.PpssppActivity", ACTION_VIEW, data = true),
        Recipe("ps2", "AetherSX2", AETHER, "$AETHER.EmulationActivity", ACTION_MAIN, extra = "bootPath"),
        Recipe("ps2", "AetherSX2", "xyz.aethersx2.tturnip", "$AETHER.EmulationActivity", ACTION_MAIN, extra = "bootPath"),
        Recipe("gc", "Dolphin", DOLPHIN, "$DOLPHIN.ui.main.MainActivity", ACTION_VIEW, extra = "AutoStartFile"),
        Recipe("wii", "Dolphin", DOLPHIN, "$DOLPHIN.ui.main.MainActivity", ACTION_VIEW, extra = "AutoStartFile"),
        Recipe("wiiu", "Cemu", "info.cemu.cemu", "info.cemu.cemu.emulation.EmulationActivity", ACTION_VIEW, data = true),
        Recipe("dreamcast", "redream", "io.recompiled.redream", "io.recompiled.redream.MainActivity", ACTION_VIEW, data = true),
        Recipe("switch", "Kenji-NX", "org.kenjinx.android", "org.kenjinx.android.MainActivity", "org.kenjinx.android.LAUNCH_GAME", extra = "bootPath"),
        Recipe("switch", "Eden", null, "org.yuzu.yuzu_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", data = true),
    )

    private val dataFolders = setOf(
        "bios", "firmware", "cache", "savedata", "saves", "save", "memcards", "sstates", "savestates", "textures", "shaders",
        "covers", "cheats", "logs", "system", "ppsspp_state", "ux0", "nand", "mlc01", "keys", "config", "dlc", "updates", "update",
    )

    fun scan(root: File): List<Game> {
        val out = ArrayList<Game>()
        val tops = root.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name.lowercase() }
        for (top in tops) {
            val system = systems.firstOrNull { top.name.lowercase() in it.folders } ?: continue
            val dirs = top.walkTopDown().maxDepth(3).onEnter { it == top || it.name.lowercase() !in dataFolders }.filter { it.isDirectory }
            for (dir in dirs) {
                val files = dir.listFiles()?.sortedBy { it.name.lowercase() }.orEmpty().filter { it.isFile }
                val sheets = files.filter { it.extension.lowercase() in Library.sheetExts }
                val parts = sheets.flatMap { Library.sheetParts(it) }.toSet()
                for (f in files) {
                    val ext = f.extension.lowercase()
                    if (ext in system.exts && ext != "bin" && f.name.lowercase() !in parts) out += Game(system, titleOf(f), f, f.lastModified())
                }
                if (system.trackOfSheet) for (sheet in sheets.filter { it.extension.lowercase() == "cue" }) {
                    val track = Library.sheetParts(sheet).firstOrNull()?.let { name -> files.firstOrNull { it.name.lowercase() == name } } ?: continue
                    out += Game(system, Library.displayTitle(sheet.nameWithoutExtension), track, track.lastModified())
                }
            }
        }
        return out.distinctBy { it.file.path }
    }

    private fun titleOf(f: File): String =
        Library.displayTitle(if (f.name.equals("EBOOT.PBP", ignoreCase = true)) f.parentFile?.name ?: f.nameWithoutExtension else f.nameWithoutExtension)

    fun coverRom(game: Game): Library.Rom? = game.system.coverId?.let { Library.Rom(game.title, game.file, "", it) }

    private val primaryRoots = listOf("/storage/emulated/0", "/sdcard", "/storage/self/primary", "/mnt/sdcard")
    private val volume = Regex("^/storage/([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})(/.*)?$")

    fun documentId(path: String): String? {
        val clean = path.trimEnd('/')
        for (root in primaryRoots) {
            if (clean == root) return "primary:"
            if (clean.startsWith("$root/")) return "primary:" + clean.removePrefix("$root/")
        }
        val m = volume.find(clean) ?: return null
        return m.groupValues[1] + ":" + m.groupValues[2].removePrefix("/")
    }

    fun covers(treeId: String, documentId: String): Boolean =
        documentId == treeId || documentId.startsWith(if (treeId.endsWith(":")) treeId else "$treeId/")

    fun treeUri(treeId: String): String = "content://$STORAGE_AUTHORITY/tree/" + encode(treeId)

    fun documentUri(treeId: String, documentId: String): String = treeUri(treeId) + "/document/" + encode(documentId)

    private const val UNRESERVED = "_-!.~'()*"

    private fun encode(s: String): String {
        val out = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in UNRESERVED) out.append(c)
            else out.append('%').append("%02X".format(b.toInt() and 0xFF))
        }
        return out.toString()
    }

    fun launch(target: Installed, documentUri: String, path: String): Launch {
        val r = target.recipe
        return Launch(
            target.packageName, r.activity, r.action, documentUri.takeIf { r.data },
            r.extra?.let { mapOf(it to if (r.extraIsPath) path else documentUri) }.orEmpty(),
        )
    }

    fun installed(hasActivity: (String, String) -> String?, launcherPackages: () -> List<String>): Map<String, List<Installed>> {
        val out = LinkedHashMap<String, MutableList<Installed>>()
        for (r in recipes) {
            val packages = if (r.packageName != null) listOf(r.packageName) else launcherPackages()
            for (pkg in packages) {
                val label = hasActivity(pkg, r.activity) ?: continue
                val list = out.getOrPut(r.system) { ArrayList() }
                if (list.none { it.packageName == pkg }) list += Installed(r, pkg, label.ifEmpty { r.name })
            }
        }
        return out
    }

    fun pick(candidates: List<Installed>, chosen: String?): Installed? =
        candidates.firstOrNull { it.packageName == chosen } ?: candidates.singleOrNull()
}
