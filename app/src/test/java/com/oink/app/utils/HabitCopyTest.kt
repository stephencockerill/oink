package com.oink.app.utils

import com.oink.app.data.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for HabitCopy.
 *
 * Pure string mappings with no dependencies. The point of these tests is to pin
 * the approved copy so a well-meaning edit can't silently reintroduce
 * habit-specific vocabulary (a single habit's verb) or drop an urgency level.
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
    fun `rewards stat label is habit-neutral`() {
        assertEquals("Days", HabitCopy.STAT_DAYS)
    }

    @Test
    fun `reward description is habit-neutral`() {
        assertEquals("How much you earn each day you show up", HabitCopy.REWARD_DESCRIPTION)
    }

    @Test
    fun `dayCount is singular for one day`() {
        assertEquals("1 day", HabitCopy.dayCount(1))
    }

    @Test
    fun `dayCount is plural for zero and many days`() {
        assertEquals("0 days", HabitCopy.dayCount(0))
        assertEquals("3 days", HabitCopy.dayCount(3))
    }

    @Test
    fun `empty history prompt is habit-neutral`() {
        assertEquals("Log your first check-in to get started!", HabitCopy.EMPTY_HISTORY)
    }

    @Test
    fun `notification prompt keeps the piggy-bank charm`() {
        assertEquals("Did you check in today? Add to your piggy bank!", HabitCopy.NOTIFICATION_PROMPT)
    }

    // =====================================================================
    // Type-branched copy - build arms must equal the existing build consts so
    // build habits read exactly as before; quit arms carry the new wording.
    // =====================================================================

    @Test
    fun `build arms return the original build copy verbatim`() {
        assertEquals(HabitCopy.CHECK_IN_PROMPT, HabitCopy.checkInPrompt(HabitType.BUILD))
        assertEquals(HabitCopy.CHECK_IN_PROMPT_PAST, HabitCopy.checkInPromptPast(HabitType.BUILD))
        assertEquals(HabitCopy.DONE, HabitCopy.successStatus(HabitType.BUILD))
        assertEquals(HabitCopy.REST, HabitCopy.failureStatus(HabitType.BUILD))
        assertEquals(HabitCopy.DONE_SUBTITLE, HabitCopy.successSubtitle(HabitType.BUILD))
        assertEquals(HabitCopy.REST_SUBTITLE, HabitCopy.failureSubtitle(HabitType.BUILD))
        assertEquals(HabitCopy.HISTORY_DONE, HabitCopy.historySuccess(HabitType.BUILD))
        assertEquals(HabitCopy.HISTORY_REST, HabitCopy.historyFailure(HabitType.BUILD))
        assertEquals(HabitCopy.LOGGED_DONE, HabitCopy.loggedSuccess(HabitType.BUILD))
        assertEquals(HabitCopy.LOGGED_REST, HabitCopy.loggedFailure(HabitType.BUILD))
        assertEquals(HabitCopy.LEGEND_DONE, HabitCopy.legendSuccess(HabitType.BUILD))
        assertEquals("Missed", HabitCopy.legendFailure(HabitType.BUILD))
        assertEquals(HabitCopy.STAT_DONE_DAYS, HabitCopy.statSuccessDays(HabitType.BUILD))
        assertEquals("Missed\nDays", HabitCopy.statFailureDays(HabitType.BUILD))
        assertEquals("🐷 Piggy Bank", HabitCopy.balanceLabel(HabitType.BUILD))
        assertEquals("Streak in danger!", HabitCopy.freezePromptTitle(HabitType.BUILD))
        assertEquals(
            "You missed Yesterday. Use a freeze to save your streak!",
            HabitCopy.freezePromptBody(HabitType.BUILD, "Yesterday")
        )
        assertEquals("Yes!", HabitCopy.confirmSuccess(HabitType.BUILD))
        assertEquals("No", HabitCopy.confirmFailure(HabitType.BUILD))
    }

    @Test
    fun `quit arms are clean-slip framed and never cue the behavior`() {
        assertEquals("Staying clean", HabitCopy.checkInPrompt(HabitType.QUIT))
        assertEquals("How did this day go?", HabitCopy.checkInPromptPast(HabitType.QUIT))
        assertEquals("✅ Stayed clean", HabitCopy.successStatus(HabitType.QUIT))
        assertEquals("🫂 Slipped", HabitCopy.failureStatus(HabitType.QUIT))
        assertEquals("You stayed clean today", HabitCopy.successSubtitle(HabitType.QUIT))
        assertEquals("Slips happen - protect your clean streak", HabitCopy.failureSubtitle(HabitType.QUIT))
        assertEquals("Clean 💪", HabitCopy.historySuccess(HabitType.QUIT))
        assertEquals("Slip", HabitCopy.historyFailure(HabitType.QUIT))
        assertEquals("You logged this as a clean day ✓", HabitCopy.loggedSuccess(HabitType.QUIT))
        assertEquals("You logged a slip on this day", HabitCopy.loggedFailure(HabitType.QUIT))
        assertEquals("Clean", HabitCopy.legendSuccess(HabitType.QUIT))
        assertEquals("Slip", HabitCopy.legendFailure(HabitType.QUIT))
        assertEquals("Clean\nDays", HabitCopy.statSuccessDays(HabitType.QUIT))
        assertEquals("Slips", HabitCopy.statFailureDays(HabitType.QUIT))
        assertEquals("🐷 Protected balance", HabitCopy.balanceLabel(HabitType.QUIT))
        assertEquals("Slips happen", HabitCopy.freezePromptTitle(HabitType.QUIT))
        assertEquals(
            "You slipped Yesterday. Spend a freeze to protect your clean streak.",
            HabitCopy.freezePromptBody(HabitType.QUIT, "Yesterday")
        )
        assertEquals("Clean", HabitCopy.confirmSuccess(HabitType.QUIT))
        assertEquals("Slipped", HabitCopy.confirmFailure(HabitType.QUIT))
        assertEquals("I slipped", HabitCopy.SLIP_ACTION)
        assertEquals("Actually, I didn't slip", HabitCopy.UNDO_SLIP)
    }

    @Test
    fun `quit celebration is streak-framed and pluralizes cleanly`() {
        assertEquals("Look at you go! 🐷", HabitCopy.QUIT_CELEBRATION_TITLE)
        assertEquals("1 clean day and counting - keep it going!", HabitCopy.quitCelebrationBody(1))
        assertEquals("12 clean days and counting - keep it going!", HabitCopy.quitCelebrationBody(12))
    }

    @Test
    fun `quit celebration body never names a behavior - only the streak`() {
        // The body must carry only the streak number, so it can never become a
        // craving cue. Assert it is exactly the streak-count sentence.
        for (streak in intArrayOf(1, 3, 40)) {
            val body = HabitCopy.quitCelebrationBody(streak)
            assertEquals("$streak clean day${if (streak == 1) "" else "s"} and counting - keep it going!", body)
        }
        assertFalse(HabitCopy.QUIT_CELEBRATION_TITLE.isBlank())
    }
}
