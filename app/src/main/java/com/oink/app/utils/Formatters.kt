package com.oink.app.utils

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Utility functions for formatting values in the app.
 *
 * Why put these in a separate file? Because formatting logic
 * tends to be reused across multiple screens, and duplicating
 * it would be some amateur hour bullshit.
 */
object Formatters {

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE")

    /**
     * Format a balance as currency.
     * e.g., 42.5 -> "$42.50"
     */
    fun formatCurrency(amount: Double): String {
        return currencyFormatter.format(amount)
    }

    /**
     * Format a date in a human-readable medium format.
     * e.g., "Dec 15, 2024"
     */
    fun formatDate(date: LocalDate): String {
        return dateFormatter.format(date)
    }

    /**
     * Format a date in short format.
     * e.g., "Dec 15"
     */
    fun formatDateShort(date: LocalDate): String {
        return shortDateFormatter.format(date)
    }

    /**
     * Format a date relative to today.
     * e.g., "Today", "Yesterday", "Monday", "Dec 15"
     */
    fun formatDateRelative(date: LocalDate): String {
        val today = LocalDate.now()
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.isAfter(today.minusDays(7)) -> dayOfWeekFormatter.format(date)
            else -> formatDateShort(date)
        }
    }

    /**
     * Format a streak count.
     * e.g., 0 -> "No streak", 1 -> "1 day", 5 -> "5 days"
     */
    fun formatStreak(days: Int): String {
        return when (days) {
            0 -> "No streak"
            1 -> "1 day"
            else -> "$days days"
        }
    }

    /**
     * Format streak with fire emoji because we're not fucking boring.
     */
    fun formatStreakWithEmoji(days: Int): String {
        return when {
            days == 0 -> "No streak"
            days < 7 -> "🔥 $days day${if (days > 1) "s" else ""}"
            days < 30 -> "🔥🔥 $days days"
            else -> "🔥🔥🔥 $days days"
        }
    }
}

