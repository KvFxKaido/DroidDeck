package com.droiddeck.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDockTest {
    @Test
    fun anUnsetDockIsTheFiveDroidDeckPages() {
        assertEquals(DockPrefs.DEFAULT, dockParse(null))
        assertEquals(listOf("page:games", "page:stores", "page:android-apps", "page:setup", "page:steam"), DockPrefs.DEFAULT)
    }

    @Test
    fun theLibraryCannotBeLostOrRemoved() {
        assertEquals(listOf("page:games", "app:com.android.chrome/.Main"), dockParse("app:com.android.chrome/.Main"))
        assertEquals(listOf("page:games"), dockRemove(listOf("page:games"), "page:games"))
    }

    @Test
    fun unknownAndDuplicateItemsAreDropped() {
        assertEquals(listOf("page:games", "page:steam"), dockParse("page:games\npage:nowhere\npage:steam\npage:steam\napp:broken\n"))
    }

    @Test
    fun appIdsRoundTrip() {
        val id = appDockId("com.android.vending", "com.android.vending.AssetBrowserActivity")
        assertEquals("com.android.vending" to "com.android.vending.AssetBrowserActivity", dockApp(id))
        assertNull(dockApp("page:games"))
        assertEquals("stores", dockPage("page:stores"))
    }

    @Test
    fun popoutAppsToggleAndParseCleanly() {
        val one = popoutToggle(emptyList(), "app:com.google.android.gm/.Main")
        val two = popoutToggle(one, "app:com.facebook.orca/.Main")
        assertEquals(listOf("app:com.google.android.gm/.Main", "app:com.facebook.orca/.Main"), two)
        assertEquals(listOf("app:com.facebook.orca/.Main"), popoutToggle(two, "app:com.google.android.gm/.Main"))
        assertEquals(two + "page:stores", popoutParse(two.joinToString("\n") + "\npage:stores\npage:nowhere\napp:com.facebook.orca/.Main\n"))
        assertEquals("droiddeck.dock/stores", pageIconApp("stores", "Stores").key)
    }

    @Test
    fun addingTwiceKeepsOneAndMovingStaysInBounds() {
        val list = dockAdd(dockAdd(listOf("page:games"), "app:a/b"), "app:a/b")
        assertEquals(listOf("page:games", "app:a/b"), list)
        assertEquals(listOf("app:a/b", "page:games"), dockMove(list, "app:a/b", -1))
        assertEquals(list, dockMove(list, "app:a/b", 1))
        assertEquals(list, dockMove(list, "page:games", -1))
    }
}
