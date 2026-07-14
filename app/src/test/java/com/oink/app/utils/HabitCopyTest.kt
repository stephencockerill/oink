package com.oink.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for HabitCopy.
 *
 * Pure string mappings with no dependencies. The point of these tests is to pin
 * the approved copy so a well-meaning edit can't silently reintroduce
 * habit-specific ("workout") vocabulary or drop an urgency level.
 */
class HabitCopyTest {

    // =====================================================================
    // cta() - one entry per urgency level
    // =====================================================================

    @Test
    fun `cta CALM is the calm log prompt`() {
        assertEquals("🐷 Log it today", HabitCopy.cta(UrgencyLevel.CALM))
    }

    @Test
    fun `cta NUDGE nudges without breaking the streak`() {
        assertEquals("Don't miss today!", HabitCopy.cta(UrgencyLevel.NUDGE))
    }

    @Test
    fun `cta WARN warns about the streak`() {
        assertEquals("Don't break the streak!", HabitCopy.cta(UrgencyLevel.WARN))
    }

    @Test
    fun `cta CRITICAL is the urgent log now prompt`() {
        assertEquals("⚡ LOG NOW!", HabitCopy.cta(UrgencyLevel.CRITICAL))
    }

    @Test
    fun `cta maps every urgency level to a non-blank string`() {
        for (level in UrgencyLevel.entries) {
            assertEquals(false, HabitCopy.cta(level).isBlank())
        }
    }

    // =====================================================================
    // Status + prompt fields
    // =====================================================================

    @Test
    fun `done status is neutral celebration`() {
        assertEquals("✅ Nailed it!", HabitCopy.DONE)
    }

    @Test
    fun `rest status is neutral off day`() {
        assertEquals("😴 Off day", HabitCopy.REST)
    }

    @Test
    fun `check-in prompt is habit-neutral`() {
        assertEquals("Did you do it today?", HabitCopy.CHECK_IN_PROMPT)
    }

    @Test
    fun `past check-in prompt is habit-neutral`() {
        assertEquals("Did you do it on this day?", HabitCopy.CHECK_IN_PROMPT_PAST)
    }

    @Test
    fun `detail subtitles are habit-neutral`() {
        assertEquals("You did it today", HabitCopy.DONE_SUBTITLE)
        assertEquals("Tomorrow's another chance", HabitCopy.REST_SUBTITLE)
    }

    @Test
    fun `history row labels are habit-neutral`() {
        assertEquals("Done 💪", HabitCopy.HISTORY_DONE)
        assertEquals("Off day", HabitCopy.HISTORY_REST)
    }

    @Test
    fun `calendar log confirmations are habit-neutral`() {
        assertEquals("You checked in on this day ✓", HabitCopy.LOGGED_DONE)
        assertEquals("You logged this as an off day", HabitCopy.LOGGED_REST)
    }

    @Test
    fun `undo buttons are habit-neutral`() {
        assertEquals("Actually, I didn't", HabitCopy.UNDO_DONE)
        assertEquals("Wait, I did!", HabitCopy.UNDO_REST)
    }

    @Test
    fun `positive-action word is a single shared value across surfaces`() {
        assertEquals("Done", HabitCopy.ACTION_DONE)
        assertEquals(HabitCopy.ACTION_DONE, HabitCopy.PREVIEW_DONE)
        assertEquals(HabitCopy.ACTION_DONE, HabitCopy.LEGEND_DONE)
        assertEquals(HabitCopy.ACTION_DONE, HabitCopy.CONTENT_DESC_DONE)
        assertEquals("Done!", HabitCopy.CONFIRM_DONE)
    }

    @Test
    fun `history stat label is habit-neutral and keeps its line break`() {
        assertEquals("Active\nDays", HabitCopy.STAT_DONE_DAYS)
    }

    @Test
    fun `empty history prompt is habit-neutral`() {
        assertEquals("Log your first check-in to get started!", HabitCopy.EMPTY_HISTORY)
    }

    @Test
    fun `notification prompt keeps the piggy-bank charm`() {
        assertEquals("Did you check in today? Add to your piggy bank!", HabitCopy.NOTIFICATION_PROMPT)
    }
}
