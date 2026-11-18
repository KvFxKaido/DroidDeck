package com.droiddeck.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EntryNamesTest {
    private fun entry(key: String, title: String) = LibraryEntry(key, "added", title, null, null, installed = true)

    @Test
    fun aRenamedEntryKeepsItsKeyAndTakesTheNewTitle() {
        val out = renamedEntries(listOf(entry("a", "MC3 Remix"), entry("b", "Bravo")), mapOf("a" to "Midnight Club 3"))
        assertEquals(listOf("a" to "Midnight Club 3", "b" to "Bravo"), out.map { it.key to it.title })
    }

    @Test
    fun noNamesLeavesTheListAlone() {
        val list = listOf(entry("a", "Alpha"))
        assertSame(list, renamedEntries(list, emptyMap()))
    }

    @Test
    fun theShelfSortsOnTheNewName() {
        val all = renamedEntries(listOf(entry("z", "Zzz"), entry("y", "Yyy")), mapOf("z" to "Aardvark"))
        assertEquals(listOf("z", "y"), shelfEntries(all, setOf("y", "z")).map { it.key })
    }
}
