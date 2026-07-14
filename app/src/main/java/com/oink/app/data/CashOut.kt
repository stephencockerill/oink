package com.oink.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Record of cashing out from the piggy bank.
 *
 * This is the REWARD for all your hard work! When you've earned enough
 * through consistent check-ins, you can treat yourself to something nice.
 *
 * The key psychological insight: this should feel like a CELEBRATION,
 * not a loss. You EARNED this reward through effort and discipline.
 */
@Entity(tableName = "cash_outs")
data class CashOut(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * What you're treating yourself to.
     * e.g., "New Darts Set", "Guitar Strings", "Fancy Coffee"
     */
    val name: String,

    /**
     * How much you're cashing out, in cents.
     */
    val amount: Long,

    /**
     * Emoji to represent your reward.
     * Makes it more personal and fun!
     */
    val emoji: String = "🎁",

    /**
     * When you cashed out (epoch millis).
     */
    val cashedOutAt: Long = System.currentTimeMillis(),

    /**
     * Balance before cashing out, in cents.
     * Useful for showing "look how much you had saved!"
     */
    val balanceBefore: Long,

    /**
     * Balance after cashing out, in cents.
     */
    val balanceAfter: Long
) {
    /**
     * Approximate how many completed days it took to earn this reward.
     *
     * A cash-out is pot-level; the per-habit reward rate that funded it lives
     * in [CashOutAllocation.rewardAtTime]. Until allocation-aware reads land,
     * this display divides by the default per-day reward
     * ([PreferencesRepository.DEFAULT_DAILY_REWARD]), which is exact for every
     * user whose reward has only ever been the default.
     */
    val daysToEarn: Int
        get() = (amount / PreferencesRepository.DEFAULT_DAILY_REWARD).toInt()
}

/**
 * Pre-defined reward categories with suggested emojis.
 * Makes it easy for users to quickly log common treats.
 */
object RewardCategories {
    val suggestions = listOf(
        "🎮" to "Gaming",
        "🎸" to "Music",
        "👕" to "Clothing",
        "☕" to "Food & Drink",
        "📚" to "Books",
        "🎯" to "Sports/Hobbies",
        "🎬" to "Entertainment",
        "💆" to "Self-care",
        "🏋️" to "Gym gear",
        "🎁" to "Other"
    )

    val defaultEmojis = listOf("🎁", "🎉", "🛍️", "💰", "🏆", "⭐", "🌟", "💎", "🎊", "🥳")
}

