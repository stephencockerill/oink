package com.oink.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Record of cashing out from the piggy bank.
 *
 * This is the REWARD for all your hard work! When you've earned enough
 * through consistent exercise, you can treat yourself to something nice.
 *
 * The key psychological insight: this should feel like a CELEBRATION,
 * not a loss. You EARNED this reward through sweat and discipline.
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
     * How much you're cashing out.
     */
    val amount: Double,

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
     * Balance before cashing out.
     * Useful for showing "look how much you had saved!"
     */
    val balanceBefore: Double,

    /**
     * Balance after cashing out.
     */
    val balanceAfter: Double,

    /**
     * The exercise reward amount at the time of this cash-out.
     * We store this because the user can change their reward setting,
     * but we want to accurately show "X workouts earned this" based on
     * what the reward was when they actually earned the money.
     */
    val exerciseRewardAtTime: Double = 5.0
) {
    /**
     * Calculate how many workouts it took to earn this reward.
     * Uses the reward amount that was set at the time of cash-out.
     */
    val workoutsToEarn: Int
        get() = if (exerciseRewardAtTime > 0) (amount / exerciseRewardAtTime).toInt() else 0
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

