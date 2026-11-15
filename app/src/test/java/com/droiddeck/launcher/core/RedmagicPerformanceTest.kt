package com.droiddeck.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RedmagicPerformanceTest {
    private val pkg = "com.droiddeck.launcher"
    private val list = "com.a.game+200,com.droiddeck.launcher+200,com.droiddeck.launcher.shortcuts+340,org.b.game+300,"

    private fun read(list: String = this.list, diablo: String = "") = RedmagicPerformance.Read(list, diablo)

    @Test fun onlyDroidDecksEntryChangesAndTheRestIsByteForByteTheSame() {
        val changed = RedmagicPerformance.withEntry(list, pkg, "300")
        assertEquals("com.a.game+200,com.droiddeck.launcher+300,com.droiddeck.launcher.shortcuts+340,org.b.game+300,", changed)
        assertEquals(list, RedmagicPerformance.withEntry(changed!!, pkg, "200"))
        assertEquals("200", RedmagicPerformance.entry(list, pkg))
        assertEquals("340", RedmagicPerformance.entry(list, "com.droiddeck.launcher.shortcuts"))
    }

    @Test fun aListWithoutDroidDeckOrAnOddValueIsNotWritten() {
        assertNull(RedmagicPerformance.withEntry("com.a.game+200,org.b.game+300", pkg, "300"))
        assertNull(RedmagicPerformance.entry("com.a.game+200", pkg))
        assertNull(RedmagicPerformance.withEntry(list, pkg, "3 00"))
        assertNull(RedmagicPerformance.withEntry(list, pkg, "1; reboot"))
        assertNull(RedmagicPerformance.entry("com.droiddeck.launcher+abc", pkg))
    }

    @Test fun readingSplitsTheModeListFromTheDiabloList() {
        val got = RedmagicPerformance.parse("$list\n---droiddeck---\ncom.x.game,com.y.game,\n")
        assertEquals(list, got?.list)
        assertEquals("com.x.game,com.y.game,", got?.diablo)
        assertEquals("", RedmagicPerformance.parse("$list\n---droiddeck---\nnull\n")?.diablo)
        assertNull(RedmagicPerformance.parse("null\n---droiddeck---\nnull\n"))
        assertNull(RedmagicPerformance.parse("com.a.game+200,evil quote+300\n---droiddeck---\n\n"))
        assertNull(RedmagicPerformance.parse("no separator"))
    }

    @Test fun theWriteCommandOnlyEverQuotesASafeList() {
        assertEquals("settings put global NubiaperformanceMode '$list'", RedmagicPerformance.writeCommand(list))
        assertThrows(IllegalArgumentException::class.java) { RedmagicPerformance.writeCommand("a+1'; rm -rf /") }
        assertThrows(IllegalArgumentException::class.java) { RedmagicPerformance.writeCommand("a+1 b+2") }
    }

    @Test fun nothingIsWrittenWhenTheModeIsAlreadySetOrDiabloIsOn() {
        assertNull(RedmagicPerformance.change(read(), pkg, "200"))
        assertTrue(RedmagicPerformance.change(read(), pkg, "300")!!.contains("com.droiddeck.launcher+300,"))
        assertNull(RedmagicPerformance.change(read(diablo = "com.x.game,com.droiddeck.launcher,"), pkg, "100"))
        assertTrue(RedmagicPerformance.inDiablo("com.droiddeck.launcher,", pkg))
        assertFalse(RedmagicPerformance.inDiablo("com.droiddeck.launcher.shortcuts,", pkg))
    }

    @Test fun aGamesOwnChoiceBeatsTheDefaultIncludingLeavingItToRedmagic() {
        assertEquals(RedmagicPerformance.RISE, RedmagicPerformance.pick(RedmagicPerformance.RISE, RedmagicPerformance.BALANCE))
        assertEquals(RedmagicPerformance.BALANCE, RedmagicPerformance.pick(null, RedmagicPerformance.BALANCE))
        assertEquals(RedmagicPerformance.LEAVE, RedmagicPerformance.pick(RedmagicPerformance.LEAVE, RedmagicPerformance.RISE))
    }
}
