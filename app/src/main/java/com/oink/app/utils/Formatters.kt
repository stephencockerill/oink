package com.oink.app.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Utility functions for formatting values in the app.
 *
 * Why put these in a separate file? Because formatting logic
 * tends to be reused across multiple screens, and duplicating
 * it everywhere would be a maintenance headache.
 */
object Formatters {

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE")

    /**
     * Format a balance (in cents) as currency.
     * e.g., 4250 -> "$42.50"
     */
    fun formatCurrency(cents: Long): String {
        return currencyFormatter.format(BigDecimal(cents).movePointLeft(2))
    }

    /**
     * Parse a user-entered dollar string into cents.
     * e.g., "42.5" -> 4250, "12.34" -> 1234
     * Returns null if the text isn't a valid number.
     */
    fun parseDollarsToCents(text: String): Long? {
        val value = text.trim().toBigDecimalOrNull() ?: return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /**
     * Format cents as a plain dollar string for editing in a text field.
     * e.g., 2000 -> "20.00"
     */
    fun centsToInput(cents: Long): String {
        return BigDecimal(cents).movePointLeft(2).toPlainString()
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
     * Format streak with fire emoji for a bit of flair.
     */
    fun formatStreakWithEmoji(days: Int): String {
        return when {
            days == 0 -> "No streak"
            days < 7 -> "🔥 $days day${if (days > 1) "s" else ""}"
            days < 30 -> "🔥🔥 $days days"
            else -> "🔥🔥🔥 $days days"
        }
    }

    /**
     * Format a date from epoch milliseconds.
     * Uses relative formatting for recent dates.
     */
    fun formatDateFromMillis(millis: Long): String {
        val date = Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return formatDateRelative(date)
    }
}

