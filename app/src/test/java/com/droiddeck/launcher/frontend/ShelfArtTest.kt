package com.droiddeck.launcher.frontend

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShelfArtTest {
    private fun tempDir(): File = Files.createTempDirectory("shelf-art").toFile()

    @Test
    fun aMissingSquareIsFetched() {
        assertTrue(ShelfArt.needsFetch(tempDir(), now = 1_000L, keyChangedAt = 0L))
    }

    @Test
    fun aSquareOnDiskIsNotFetchedAgain() {
        val dir = tempDir()
        File(dir, "square").writeBytes(byteArrayOf(1, 2, 3))
        assertFalse(ShelfArt.needsFetch(dir, now = 1_000L, keyChangedAt = 0L))
        assertEquals("square", ShelfArt.squareIn(dir)?.name)
    }

    @Test
    fun anEmptySquareFileDoesNotCount() {
        val dir = tempDir()
        File(dir, "square").writeBytes(ByteArray(0))
        assertNull(ShelfArt.squareIn(dir))
    }

    @Test
    fun aRecentMissWaitsAWeekUnlessTheKeyChanged() {
        val dir = tempDir()
        val tried = File(dir, "auto-tried").apply { writeText("") }
        val at = tried.lastModified()
        assertFalse(ShelfArt.needsFetch(dir, now = at + 1_000L, keyChangedAt = at - 1_000L))
        assertTrue(ShelfArt.needsFetch(dir, now = at + 8L * 24 * 3600 * 1000, keyChangedAt = at - 1_000L))
        assertTrue(ShelfArt.needsFetch(dir, now = at + 1_000L, keyChangedAt = at + 500L))
    }

    private fun icon(url: String, size: Int, score: Int, mime: String = "image/png") =
        org.json.JSONObject().put("url", url).put("width", size).put("height", size).put("score", score).put("mime", mime)

    @Test
    fun theBestLargePngIconIsPicked() {
        val items = org.json.JSONArray()
            .put(icon("https://a/small.png", 32, 99))
            .put(icon("https://a/good.png", 256, 5))
            .put(icon("https://a/better.png", 512, 7))
            .put(icon("https://a/ico.ico", 512, 50, mime = "image/vnd.microsoft.icon"))
        assertEquals("https://a/better.png", ShelfArt.bestIcon(items))
    }

    @Test
    fun onlySmallIconsMeansNoIcon() {
        assertNull(ShelfArt.bestIcon(org.json.JSONArray().put(icon("https://a/small.png", 128, 9))))
    }

    @Test
    fun aPickBeatsTheAutomaticSquare() {
        val dir = tempDir()
        File(dir, "square").writeBytes(byteArrayOf(1))
        File(dir, "chosen-icon-0000000000001.png").writeBytes(byteArrayOf(2))
        assertEquals("chosen-icon-0000000000001.png", ShelfArt.shownIn(dir)?.name)
    }

    @Test
    fun resetStepsFromPickToAutomaticToCoverAndBack() {
        val dir = tempDir()
        File(dir, "square").writeBytes(byteArrayOf(1))
        File(dir, "chosen-icon-0000000000001.png").writeBytes(byteArrayOf(2))
        ShelfArt.resetIn(dir)
        assertEquals("square", ShelfArt.shownIn(dir)?.name)
        ShelfArt.resetIn(dir)
        assertNull(ShelfArt.shownIn(dir))
        assertFalse(ShelfArt.needsFetch(dir, now = 1_000L, keyChangedAt = 0L))
        ShelfArt.resetIn(dir)
        assertTrue(ShelfArt.needsFetch(dir, now = 1_000L, keyChangedAt = 0L))
    }

    @Test
    fun theChooserListsLargePngIconsBestFirst() {
        val items = org.json.JSONArray()
            .put(icon("https://a/small.png", 64, 99))
            .put(icon("https://a/b.png", 256, 1))
            .put(icon("https://a/a.png", 512, 8))
        assertEquals(listOf("https://a/a.png", "https://a/b.png"), ShelfArt.iconOptions(items).map { it.full })
    }

    @Test
    fun aRateLimitPausesForTenMinutes() {
        assertEquals(10L * 60 * 1000 + 5L, ShelfArt.pauseAfter(SteamGridDb.Status.LIMIT, 5L))
        assertEquals(0L, ShelfArt.pauseAfter(SteamGridDb.Status.OFFLINE, 5L))
    }
}
