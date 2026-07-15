package com.oink.app.viewmodel

import com.oink.app.data.CheckInRepository
import com.oink.app.data.DefaultDeductionProvider
import com.oink.app.data.FakeCashOutAllocationDao
import com.oink.app.data.FakeCashOutDao
import com.oink.app.data.FakeCheckInDao
import com.oink.app.data.FakeFrozenDayDao
import com.oink.app.data.FakeHabitDao
import com.oink.app.data.FreezeRepository
import com.oink.app.data.HabitRewardProvider
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [HeroSignalSource.aggregate], the pure fold shared by the home
 * list and Rewards heroes.
 *
 * The fold takes no repositories, so these exercise it directly against
 * hand-built [HabitSignal]s: hottest streak, summed gains, the loss-beats-gain
 * precedence on the most recent day, and the empty-set resting signal.
 */
class HeroSignalSourceTest {

    private lateinit var source: HeroSignalSource

    @Before
    fun setup() {
        val fakeHabitDao = FakeHabitDao()
        val fakeCheckInDao = FakeCheckInDao()
        val fakeFrozenDayDao = FakeFrozenDayDao()
        val freezeRepository = FreezeRepository(fakeHabitDao, fakeFrozenDayDao)
        val checkInRepository = CheckInRepository(
            fakeCheckInDao,
            HabitRewardProvider(fakeHabitDao),
            DefaultDeductionProvider(FakeCashOutDao(), FakeCashOutAllocationDao(), freezeRepository)
        )
        source = HeroSignalSource(checkInRepository, freezeRepository)
    }

    @Test
    fun `aggregate takes the hottest streak and sums the gains`() {
        val date = LocalDate.of(2026, 1, 10)
        val result = source.aggregate(
            listOf(
                HabitSignal(streak = 1, gainCents = 500L, lastCheckIn = date, balanceDelta = 500L),
                HabitSignal(streak = 4, gainCents = 300L, lastCheckIn = date, balanceDelta = 300L),
                HabitSignal(streak = 2, gainCents = 0L, lastCheckIn = date, balanceDelta = 0L)
            )
        )

        assertEquals(4, result.streak)
        assertEquals(800L, result.dailyGainCents)
    }

    @Test
    fun `aggregate delta comes only from the most recent day`() {
        val latest = LocalDate.of(2026, 1, 10)
        val older = LocalDate.of(2026, 1, 5)
        val result = source.aggregate(
            listOf(
                // An older habit's big gain must not count toward the mascot delta.
                HabitSignal(streak = 3, gainCents = 500L, lastCheckIn = older, balanceDelta = 9000L),
                HabitSignal(streak = 1, gainCents = 500L, lastCheckIn = latest, balanceDelta = 500L)
            )
        )

        assertEquals(latest, result.lastCheckIn)
        assertEquals(500L, result.balanceDelta)
    }

    @Test
    fun `aggregate lets a fresh loss beat a gain on the latest day`() {
        val latest = LocalDate.of(2026, 1, 10)
        val result = source.aggregate(
            listOf(
                HabitSignal(streak = 5, gainCents = 500L, lastCheckIn = latest, balanceDelta = 500L),
                // A halving on another habit the same day takes precedence.
                HabitSignal(streak = 0, gainCents = 0L, lastCheckIn = latest, balanceDelta = -250L)
            )
        )

        assertEquals(-250L, result.balanceDelta)
        // Gains still sum and the hottest streak still wins, independent of delta.
        assertEquals(500L, result.dailyGainCents)
        assertEquals(5, result.streak)
    }

    @Test
    fun `aggregate of an empty list is the resting signal`() {
        val result = source.aggregate(emptyList())

        assertEquals(0, result.streak)
        assertEquals(0L, result.dailyGainCents)
        assertNull(result.lastCheckIn)
        assertEquals(0L, result.balanceDelta)
    }
}
