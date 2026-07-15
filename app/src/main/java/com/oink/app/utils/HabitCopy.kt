package com.oink.app.utils

import com.oink.app.data.HabitType

/**
 * Urgency level based on time of day and whether the user has logged.
 *
 * Lives here (next to [HabitCopy]) rather than in the widget so the copy source
 * that maps each level to a call-to-action has no dependency on the widget.
 */
enum class UrgencyLevel {
    CALM,       // Logged, or early morning (before noon)
    NUDGE,      // Not logged, afternoon (12pm-5pm)
    WARN,       // Not logged, evening (5pm-9pm)
    CRITICAL    // Not logged, night (after 9pm)
}

/**
 * The single source of truth for check-in and status copy.
 *
 * Every surface (widget, detail, calendar, history, notifications) pulls its
 * check-in prompts and status labels from here so the wording can never drift
 * per-surface again. The copy is deliberately habit-neutral: it reads naturally
 * for any habit (Workout, Read, Meditate, ...) without a per-habit verb.
 *
 * The escalating urgency of [cta] and the celebrate-the-win / recoverable-miss
 * framing are intentional (see docs/habit-psychology.md); only the vocabulary is
 * neutral.
 *
 * Plain object, no DI - mirrors [Formatters].
 */
object HabitCopy {

    /** Prompt asking whether the habit was done today. */
    const val CHECK_IN_PROMPT = "Did you do it today?"

    /** Prompt asking whether the habit was done on a specific past day. */
    const val CHECK_IN_PROMPT_PAST = "Did you do it on this day?"

    /** Today's status once the habit is logged as done. */
    const val DONE = "Nailed it!"

    /** Today's status once the day is logged as an off day. */
    const val REST = "😴 Off day"

    /** Detail status-card subtitle when done. */
    const val DONE_SUBTITLE = "You did it today"

    /** Detail status-card subtitle on an off day. */
    const val REST_SUBTITLE = "Tomorrow's another chance"

    /** Timeline/history row label for a completed day. */
    const val HISTORY_DONE = "Done 💪"

    /** Timeline/history row label for an off day. */
    const val HISTORY_REST = "Off day"

    /** Calendar confirmation that a past day was logged as done. */
    const val LOGGED_DONE = "You checked in on this day ✓"

    /** Calendar confirmation that a past day was logged as an off day. */
    const val LOGGED_REST = "You logged this as an off day"

    /** Undo/change button when today is logged as done. */
    const val UNDO_DONE = "Actually, I didn't"

    /** Undo/change button when today is logged as an off day. */
    const val UNDO_REST = "Wait, I did!"

    /**
     * Positive-action word, reused across every surface that pairs it with the
     * already-neutral "Miss"/"Missed" (check-in preview, calendar legend,
     * calendar confirm button). One word everywhere so the app reads uniformly.
     */
    const val ACTION_DONE = "Done"

    /** Positive check-in preview column label (pairs with "Miss"). */
    const val PREVIEW_DONE = ACTION_DONE

    /** Calendar legend label for a completed day (pairs with "Missed"). */
    const val LEGEND_DONE = ACTION_DONE

    /** Calendar confirm ("yes, done") button label. */
    const val CONFIRM_DONE = "$ACTION_DONE!"

    /** History stat-tile label for completed days (pairs with "Missed\nDays"). */
    const val STAT_DONE_DAYS = "Active\nDays"

    /** Rewards stat-card label for the count of qualifying days. */
    const val STAT_DAYS = "Days"

    /**
     * Description of the per-day reward amount. Shared by the add-habit and
     * settings reward fields so the wording stays identical on both.
     */
    const val REWARD_DESCRIPTION = "How much you earn each day you show up"

    /** Screen-reader label for a completed day (pairs with "Missed"). */
    const val CONTENT_DESC_DONE = ACTION_DONE

    /** Accessibility label for the inline "log done today" control on a habit card. */
    const val CHECK_IN_DONE_ACTION = "Mark done today"

    /** Accessibility label for the inline "log missed today" control on a habit card. */
    const val CHECK_IN_MISSED_ACTION = "Mark missed today"

    /**
     * Accessibility label for the inline today-status control on a habit card,
     * which is tappable to flip today's outcome (the in-card undo path).
     */
    const val CHECK_IN_CHANGE_ACTION = "Change today's check-in"

    /** History empty-state prompt. */
    const val EMPTY_HISTORY = "Log your first check-in to get started!"

    /** Notification body prompt (the piggy-bank charm is kept). */
    const val NOTIFICATION_PROMPT = "Did you check in today? Add to your piggy bank!"

    /**
     * A count of qualifying days with a correctly pluralized noun.
     * e.g. 1 -> "1 day", 3 -> "3 days". Meant to be dropped into a surrounding
     * sentence ("3 days earned this!").
     */
    fun dayCount(days: Int): String = "$days ${if (days == 1) "day" else "days"}"

    /**
     * Call-to-action text, escalating in urgency as the day goes on.
     * Maps every [UrgencyLevel].
     */
    fun cta(level: UrgencyLevel): String = when (level) {
        UrgencyLevel.CALM -> "🐷 Log it today"
        UrgencyLevel.NUDGE -> "Don't miss today!"
        UrgencyLevel.WARN -> "Don't break the streak!"
        UrgencyLevel.CRITICAL -> "⚡ LOG NOW!"
    }

    // =====================================================================
    // Quit-habit copy
    //
    // A quit habit inverts the outcome: a clean day is the passive win and the
    // one affirmative action is reporting a slip. The copy therefore never asks
    // the user to "do" anything - it frames staying clean, treats a slip
    // empathetically (never shaming, per docs/habit-psychology.md), and the
    // success path is silent. The functions below branch every surface on
    // [HabitType]; the BUILD arm returns the existing build copy verbatim so
    // build habits read exactly as before.
    // =====================================================================

    /** Quit today-status once the day resolves clean. */
    const val CLEAN = "Stayed clean"

    /** Quit today-status once a slip is logged. Supportive, not shaming. */
    const val SLIPPED = "🫂 Slipped"

    /** Quit detail status-card subtitle on a clean day. */
    const val CLEAN_SUBTITLE = "You stayed clean today"

    /** Quit detail status-card subtitle after a slip. Recovery-framed. */
    const val SLIPPED_SUBTITLE = "Slips happen - protect your clean streak"

    /** Quit card + detail prompt while the day is still clean (a passive win). */
    const val STAYING_CLEAN = "Staying clean"

    /** Destructive primary action on a quit habit: the only affirmative control. */
    const val SLIP_ACTION = "I slipped"

    /** Quit undo button when today is logged as a slip. */
    const val UNDO_SLIP = "Actually, I didn't slip"

    /** Accessibility label for the inline "log a slip today" control on a quit card. */
    const val CHECK_IN_SLIP_ACTION = "Log a slip today"

    /** Accessibility label for the inline today-slip status control on a quit card. */
    const val CHECK_IN_SLIP_CHANGE_ACTION = "Change today's slip"

    /**
     * Title of the mid-morning quit-habit celebration notification. Pure pride,
     * no piggy-bank call to act.
     */
    const val QUIT_CELEBRATION_TITLE = "Look at you go! 🐷"

    /**
     * Body of the quit-habit celebration notification. Framed entirely around the
     * clean streak already earned and NEVER the behavior being avoided: naming
     * the behavior would be a craving cue (ironic-process caution). Takes only the
     * streak number, so there is nothing to leak.
     */
    fun quitCelebrationBody(cleanStreak: Int): String {
        val days = if (cleanStreak == 1) "day" else "days"
        return "$cleanStreak clean $days and counting - keep it going!"
    }

    /** Detail balance-card label: build banks a reward, quit protects a stake. */
    fun balanceLabel(type: HabitType): String = when (type) {
        HabitType.BUILD -> "🐷 Piggy Bank"
        HabitType.QUIT -> "🐷 Protected balance"
    }

    /** Today check-in prompt. */
    fun checkInPrompt(type: HabitType): String = when (type) {
        HabitType.BUILD -> CHECK_IN_PROMPT
        HabitType.QUIT -> STAYING_CLEAN
    }

    /** Past-day check-in prompt. */
    fun checkInPromptPast(type: HabitType): String = when (type) {
        HabitType.BUILD -> CHECK_IN_PROMPT_PAST
        HabitType.QUIT -> "How did this day go?"
    }

    /** Calendar dialog confirm-button label for the positive outcome. */
    fun confirmSuccess(type: HabitType): String = when (type) {
        HabitType.BUILD -> "Yes!"
        HabitType.QUIT -> "Clean"
    }

    /** Calendar dialog decline-button label for the negative outcome. */
    fun confirmFailure(type: HabitType): String = when (type) {
        HabitType.BUILD -> "No"
        HabitType.QUIT -> "Slipped"
    }

    /** Today status once the positive outcome (done / clean) is recorded. */
    fun successStatus(type: HabitType): String = when (type) {
        HabitType.BUILD -> DONE
        HabitType.QUIT -> CLEAN
    }

    /** Today status once the negative outcome (off day / slip) is recorded. */
    fun failureStatus(type: HabitType): String = when (type) {
        HabitType.BUILD -> REST
        HabitType.QUIT -> SLIPPED
    }

    /** Detail status-card subtitle for the positive outcome. */
    fun successSubtitle(type: HabitType): String = when (type) {
        HabitType.BUILD -> DONE_SUBTITLE
        HabitType.QUIT -> CLEAN_SUBTITLE
    }

    /** Detail status-card subtitle for the negative outcome. */
    fun failureSubtitle(type: HabitType): String = when (type) {
        HabitType.BUILD -> REST_SUBTITLE
        HabitType.QUIT -> SLIPPED_SUBTITLE
    }

    /** History row label for the positive outcome. */
    fun historySuccess(type: HabitType): String = when (type) {
        HabitType.BUILD -> HISTORY_DONE
        HabitType.QUIT -> "Clean 💪"
    }

    /** History row label for the negative outcome. */
    fun historyFailure(type: HabitType): String = when (type) {
        HabitType.BUILD -> HISTORY_REST
        HabitType.QUIT -> "Slip"
    }

    /** Calendar confirmation that a past day was logged as the positive outcome. */
    fun loggedSuccess(type: HabitType): String = when (type) {
        HabitType.BUILD -> LOGGED_DONE
        HabitType.QUIT -> "You logged this as a clean day ✓"
    }

    /** Calendar confirmation that a past day was logged as the negative outcome. */
    fun loggedFailure(type: HabitType): String = when (type) {
        HabitType.BUILD -> LOGGED_REST
        HabitType.QUIT -> "You logged a slip on this day"
    }

    /** Calendar legend label for the positive outcome. */
    fun legendSuccess(type: HabitType): String = when (type) {
        HabitType.BUILD -> LEGEND_DONE
        HabitType.QUIT -> "Clean"
    }

    /** Calendar legend + history label for the negative outcome. */
    fun legendFailure(type: HabitType): String = when (type) {
        HabitType.BUILD -> "Missed"
        HabitType.QUIT -> "Slip"
    }

    /** History stat-tile label for the count of positive days. */
    fun statSuccessDays(type: HabitType): String = when (type) {
        HabitType.BUILD -> STAT_DONE_DAYS
        HabitType.QUIT -> "Clean\nDays"
    }

    /** History stat-tile label for the count of negative days. */
    fun statFailureDays(type: HabitType): String = when (type) {
        HabitType.BUILD -> "Missed\nDays"
        HabitType.QUIT -> "Slips"
    }

    /** Freeze-forgiveness prompt title. */
    fun freezePromptTitle(type: HabitType): String = when (type) {
        HabitType.BUILD -> "Streak in danger!"
        HabitType.QUIT -> "Slips happen"
    }

    /**
     * Freeze-forgiveness prompt body. [formattedDate] is the human-readable day
     * (e.g. "Yesterday"). The freeze protects the streak only - the halving on a
     * slip still stands - so the copy promises to protect the streak, not undo
     * the loss.
     */
    fun freezePromptBody(type: HabitType, formattedDate: String): String = when (type) {
        HabitType.BUILD -> "You missed $formattedDate. Use a freeze to save your streak!"
        HabitType.QUIT -> "You slipped $formattedDate. Spend a freeze to protect your clean streak."
    }
}
