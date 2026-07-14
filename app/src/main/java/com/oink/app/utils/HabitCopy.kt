package com.oink.app.utils

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
    const val DONE = "✅ Nailed it!"

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
}
