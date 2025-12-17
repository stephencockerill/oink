package com.oink.app.utils

import java.time.LocalDate
import java.time.ZoneId

/**
 * Date utility functions.
 *
 * Why is this its own file? Because date handling is notoriously
 * tricky and having centralized helpers prevents timezone fuckups.
 */
object DateUtils {

    /**
     * Get today's date in the system's default timezone.
     *
     * Why not just use LocalDate.now()? Because this makes testing
     * easier - we can mock this in tests without mocking the system clock.
     */
    fun today(): LocalDate {
        return LocalDate.now(ZoneId.systemDefault())
    }

    /**
     * Check if a date is today.
     */
    fun isToday(date: LocalDate): Boolean {
        return date == today()
    }

    /**
     * Check if a date is yesterday.
     */
    fun isYesterday(date: LocalDate): Boolean {
        return date == today().minusDays(1)
    }

    /**
     * Check if a date is in the past (before today).
     */
    fun isPast(date: LocalDate): Boolean {
        return date < today()
    }

    /**
     * Check if a date is in the future (after today).
     * We shouldn't allow future check-ins, that's cheating!
     */
    fun isFuture(date: LocalDate): Boolean {
        return date > today()
    }

    /**
     * Get a range of dates from start to end (inclusive).
     */
    fun dateRange(start: LocalDate, end: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = start
        while (current <= end) {
            dates.add(current)
            current = current.plusDays(1)
        }
        return dates
    }
}

