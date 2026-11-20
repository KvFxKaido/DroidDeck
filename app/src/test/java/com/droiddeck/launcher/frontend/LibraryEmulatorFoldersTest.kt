package com.droiddeck.launcher.frontend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryEmulatorFoldersTest {
    @Test
    fun anXbox360FolderIsNotScannedAsLoosePs3Games() {
        assertTrue("xbox 360" in Library.systemFolders)
        assertTrue("xbox360" in Library.systemFolders)
    }

    @Test
    fun foldersOfTheAndroidEmulatorSystemsAreNotScannedAsLooseGames() {
        assertTrue(listOf("gamecube", "wii u", "switch", "vita", "xbox").all { it in Library.systemFolders })
    }

    @Test
    fun anUnnamedFolderIsStillScanned() {
        assertFalse("my games" in Library.systemFolders)
    }
}
