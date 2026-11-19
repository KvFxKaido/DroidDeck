package com.droiddeck.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeShelfTest {
    private fun entry(key: String, title: String) = LibraryEntry(key, "added", title, null, null, installed = true)

    @Test
    fun onlyFavoritesAreOnTheShelf() {
        val all = listOf(entry("a", "Alpha"), entry("b", "Bravo"), entry("c", "Charlie"))
        assertEquals(listOf("a", "c"), shelfEntries(all, setOf("a", "c")).map { it.key })
    }

    @Test
    fun shelfSortsByTitleIgnoringCasePunctuationAndThe() {
        val all = listOf(
            entry("1", "Metal Gear Solid V: Ground Zeroes"),
            entry("2", "Metal Gear Solid: Portable Ops Plus"),
            entry("3", "metroid Dread"),
            entry("4", "The Legend of Zelda"),
            entry("5", "MX vs. ATV Unleashed"),
            entry("6", "Kingdom Hearts"),
        )
        assertEquals(listOf("6", "4", "2", "1", "3", "5"), shelfEntries(all, all.map { it.key }.toSet()).map { it.key })
    }

    @Test
    fun installedOnlyShowsEveryInstalledEntryAndIgnoresFavorites() {
        val all = listOf(
            entry("a", "Alpha"),
            LibraryEntry("b", "epic", "Bravo", null, null, installed = false),
            entry("c", "Charlie"),
        )
        assertEquals(listOf("a", "c"), shelfEntries(all, setOf("b"), installedOnly = true).map { it.key })
    }

    @Test
    fun hiddenGamesStayOffTheShelfInBothModes() {
        val all = listOf(entry("a", "Alpha"), entry("b", "Beta"), entry("c", "Gamma"))
        val hidden: (LibraryEntry) -> Boolean = { it.key == "b" }
        assertEquals(listOf("a"), shelfEntries(all, setOf("a", "b"), hidden = hidden).map { it.key })
        assertEquals(listOf("a", "c"), shelfEntries(all, emptySet(), installedOnly = true, hidden = hidden).map { it.key })
    }

    @Test
    fun aFavoriteListedTwiceAppearsOnce() {
        val all = listOf(entry("a", "Alpha"), entry("a", "Alpha"))
        assertEquals(1, shelfEntries(all, setOf("a")).size)
    }

    @Test
    fun returningFromTheShelfIsReadOnce() {
        ShelfState.showing = true
        ShelfState.leave("games")
        assertEquals(false, ShelfState.showing)
        assertEquals("games", ShelfState.takeReturn())
        assertEquals(null, ShelfState.takeReturn())
    }
}
