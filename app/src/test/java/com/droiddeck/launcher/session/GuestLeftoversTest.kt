package com.droiddeck.launcher.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestLeftoversTest {
    private fun environ(vararg entries: String) = entries.joinToString("\u0000", postfix = "\u0000").toByteArray()

    @Test
    fun aProcessOfTheEndedSessionCarriesItsFolder() {
        val marker = "DD_DEBUG_DIR=/data/logs/2026-11-28-85-steam"
        assertTrue(GuestLeftovers.carries(environ("HOME=/home/deck", marker, "TERM=xterm"), marker))
        assertTrue(GuestLeftovers.carries(environ(marker), marker))
    }

    @Test
    fun anotherSessionsFolderOrALongerPathIsNotTheEndedSession() {
        val marker = "DD_DEBUG_DIR=/data/logs/2026-11-28-85-steam"
        assertFalse(GuestLeftovers.carries(environ("DD_DEBUG_DIR=/data/logs/2026-11-28-86-steam"), marker))
        assertFalse(GuestLeftovers.carries(environ("$marker-2"), marker))
        assertFalse(GuestLeftovers.carries(environ("X$marker"), marker))
        assertFalse(GuestLeftovers.carries(null, marker))
        assertFalse(GuestLeftovers.carries(ByteArray(0), marker))
    }

    @Test
    fun whatAMatchedProcessStartedGoesWithIt() {
        // 10 was proot's child and now belongs to init; 12 cleared its environment; 20 is unrelated
        val parents = mapOf(10 to 1, 11 to 10, 12 to 11, 13 to 12, 20 to 1, 21 to 20)
        assertEquals(setOf(10, 11, 12, 13), GuestLeftovers.withDescendants(setOf(10), parents))
        assertEquals(emptySet<Int>(), GuestLeftovers.withDescendants(emptySet(), parents))
    }
}
