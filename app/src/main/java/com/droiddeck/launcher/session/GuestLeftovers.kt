package com.droiddeck.launcher.session

import java.io.File

/** Finds the processes a session left running, also once proot is gone and they belong to init. */
object GuestLeftovers {
    private const val MARKER = "DD_DEBUG_DIR="

    fun find(rootfs: String, ended: File?, skip: Set<Int>): Set<Int> {
        val marker = ended?.let { MARKER + it.path }
        val parents = HashMap<Int, Int>()
        val matched = HashSet<Int>()
        File("/proc").listFiles { f -> f.name.all { it.isDigit() } }?.forEach { proc ->
            val pid = proc.name.toIntOrNull() ?: return@forEach
            parentOf(proc)?.let { parents[pid] = it }
            if (pid in skip) return@forEach
            val exe = runCatching { File(proc, "exe").canonicalPath }.getOrNull()
            // proot runs a guest through its loader, which is then the process's executable
            if (exe?.startsWith(rootfs) == true ||
                marker != null && carries(runCatching { File(proc, "environ").readBytes() }.getOrNull(), marker)) {
                matched.add(pid)
            }
        }
        return withDescendants(matched, parents) - skip
    }

    internal fun carries(environ: ByteArray?, marker: String): Boolean {
        if (environ == null) return false
        val wanted = marker.toByteArray()
        var start = 0
        while (start < environ.size) {
            var end = start
            while (end < environ.size && environ[end] != 0.toByte()) end++
            if (end - start == wanted.size && (0 until wanted.size).all { environ[start + it] == wanted[it] }) return true
            start = end + 1
        }
        return false
    }

    internal fun withDescendants(roots: Set<Int>, parents: Map<Int, Int>): Set<Int> {
        val out = HashSet(roots)
        var grew = true
        while (grew) {
            grew = false
            for ((pid, ppid) in parents) if (pid > 1 && ppid in out && out.add(pid)) grew = true
        }
        return out
    }

    private fun parentOf(proc: File): Int? {
        val stat = runCatching { File(proc, "stat").readText() }.getOrNull() ?: return null
        return stat.substringAfterLast(") ").split(' ').getOrNull(1)?.toIntOrNull()
    }
}
