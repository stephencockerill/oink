package com.oink.app.widget

import com.oink.app.data.HabitType
import com.oink.app.utils.UrgencyLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the widget's render-time urgency decision.
 *
 * The guarantee behind issue #95 is that a quit habit never escalates: no
 * time-based "log it now" cue, because that is an ironic-process craving trigger
 * (see docs/negative-habits.md). That rule lives in [widgetUrgency], extracted
 * from the Glance composable precisely so it can be asserted without a render
 * harness.
 */
class OinkWidgetTest {

    @Test
    fun `quit habit stays calm even late at night while unlogged`() {
        assertEquals(
            UrgencyLevel.CALM,
            widgetUrgency(HabitType.QUIT, hour = 23, checkedInToday = false)
        )
    }

    @Test
    fun `quit habit stays calm across every hour`() {
        for (hour in 0..23) {
            assertEquals(
                "quit habit must never escalate (hour=$hour)",
                UrgencyLevel.CALM,
                widgetUrgency(HabitType.QUIT, hour = hour, checkedInToday = false)
            )
        }
    }

    @Test
    fun `build habit climbs the time-based ladder while unlogged`() {
        assertEquals(UrgencyLevel.CALM, widgetUrgency(HabitType.BUILD, hour = 9, checkedInToday = false))
        assertEquals(UrgencyLevel.NUDGE, widgetUrgency(HabitType.BUILD, hour = 14, checkedInToday = false))
        assertEquals(UrgencyLevel.WARN, widgetUrgency(HabitType.BUILD, hour = 19, checkedInToday = false))
        assertEquals(UrgencyLevel.CRITICAL, widgetUrgency(HabitType.BUILD, hour = 23, checkedInToday = false))
    }

    @Test
    fun `build habit stays calm once logged, regardless of hour`() {
        assertEquals(
            UrgencyLevel.CALM,
            widgetUrgency(HabitType.BUILD, hour = 23, checkedInToday = true)
        )
    }
}
