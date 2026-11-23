package com.droiddeck.launcher.core

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RedmagicLiveModeTest {
    private lateinit var ctx: Context

    @Before fun clean() {
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("redmagic_performance", Context.MODE_PRIVATE).edit().clear().commit()
        RedmagicPerformance.restoreAsync(ctx)
    }

    @Test fun aPickDuringAGameIsThatGamesOwnModeTheSameOneItsLaunchMenuShows() {
        RedmagicPerformance.onActiveGame(ctx, "1245620")
        RedmagicPerformance.chooseNow(ctx, RedmagicPerformance.RISE)
        assertEquals(RedmagicPerformance.RISE, RedmagicPerformance.forGame(ctx, "1245620"))
        assertEquals(RedmagicPerformance.LEAVE, RedmagicPerformance.default(ctx))
        RedmagicPerformance.setForGame(ctx, "1245620", RedmagicPerformance.ECO)
        RedmagicPerformance.chooseNow(ctx, RedmagicPerformance.BALANCE)
        assertEquals(RedmagicPerformance.BALANCE, RedmagicPerformance.forGame(ctx, "1245620"))
    }

    @Test fun pickingTheDefaultDuringAGameDropsItsOwnMode() {
        RedmagicPerformance.onActiveGame(ctx, "rom:psx/game.chd")
        RedmagicPerformance.chooseNow(ctx, RedmagicPerformance.ECO)
        RedmagicPerformance.chooseNow(ctx, null)
        assertNull(RedmagicPerformance.forGame(ctx, "rom:psx/game.chd"))
        assertEquals(RedmagicPerformance.LEAVE, RedmagicPerformance.default(ctx))
    }

    @Test fun withNoGameNamedThePickIsTheDefault() {
        RedmagicPerformance.onActiveGame(ctx, " ")
        assertNull(RedmagicPerformance.activeGame)
        RedmagicPerformance.chooseNow(ctx, RedmagicPerformance.ECO)
        assertEquals(RedmagicPerformance.ECO, RedmagicPerformance.default(ctx))
        RedmagicPerformance.chooseNow(ctx, null)
        assertEquals(RedmagicPerformance.LEAVE, RedmagicPerformance.default(ctx))
    }

    @Test fun endingTheSessionForgetsTheGameSoALaterPickIsNotSavedForIt() {
        RedmagicPerformance.onActiveGame(ctx, "1245620")
        RedmagicPerformance.restoreAsync(ctx)
        assertNull(RedmagicPerformance.activeGame)
        RedmagicPerformance.chooseNow(ctx, RedmagicPerformance.RISE)
        assertNull(RedmagicPerformance.forGame(ctx, "1245620"))
        assertEquals(RedmagicPerformance.RISE, RedmagicPerformance.default(ctx))
    }
}
