package com.oink.app.data

/**
 * Whether a habit is about starting a behavior or stopping one.
 *
 * The two types share the same ledger and the same halving math - a quit "slip"
 * is arithmetically identical to a build "miss" - and differ only in how a day
 * resolves and how the app talks about it:
 *
 * - [BUILD]: passivity is failure. The user acts to claim a success ("I did it"),
 *   which earns the daily reward the moment it is logged. An unlogged past day
 *   stays a miss.
 * - [QUIT]: passivity is success. Getting through a day without the behavior is
 *   the win and cannot be observed until the day is over, so a clean day is the
 *   passive default. The one affirmative control is the slip report; an unlogged
 *   past day auto-resolves to a clean success at day-close and accrues the reward.
 *
 * Persisted as its `name` string via a Room [Converters] converter, defaulting to
 * [BUILD] so every habit that predates this column reads as a build habit.
 */
enum class HabitType {
    BUILD,
    QUIT
}
