package com.oink.app.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing cash-out operations.
 *
 * This is where the REWARD magic happens! When a user has earned
 * enough through consistent exercise, they can treat themselves.
 *
 * Key psychological framing:
 * - Cash-outs are CELEBRATIONS, not losses
 * - "You EARNED this!" energy
 * - Show how many workouts it took to earn this reward
 */
class CashOutRepository(
    private val cashOutDao: CashOutDao,
    private val checkInRepository: CheckInRepository,
    private val preferencesRepository: PreferencesRepository
) {

    /**
     * Flow of all cash-outs (most recent first).
     */
    val allCashOuts: Flow<List<CashOut>> = cashOutDao.getAllCashOutsFlow()

    /**
     * Flow of total amount cashed out all-time.
     */
    val totalCashedOut: Flow<Double> = cashOutDao.getTotalCashedOutFlow()

    /**
     * Cash out from the piggy bank! 🎉
     *
     * This is the reward for all your hard work. The balance goes down,
     * but it's a GOOD thing - you're treating yourself to something
     * you earned through discipline and sweat.
     *
     * @param name What you're treating yourself to
     * @param amount How much to cash out
     * @param emoji Emoji to represent your reward
     * @return The CashOut record, or null if insufficient balance
     */
    suspend fun cashOut(
        name: String,
        amount: Double,
        emoji: String = "🎁"
    ): CashOut? {
        // Get current balance
        val currentBalance = getCurrentBalance()

        // Can't cash out more than you have
        if (amount > currentBalance || amount <= 0) {
            return null
        }

        val balanceAfter = currentBalance - amount

        // Deduct from balance
        if (!checkInRepository.deductBalance(amount)) {
            return null
        }

        // Get the current exercise reward so we can calculate "workouts earned" accurately
        // even if the user changes their reward setting later
        val exerciseReward = preferencesRepository.getExerciseReward()

        // Record the cash-out
        val cashOut = CashOut(
            name = name,
            amount = amount,
            emoji = emoji,
            balanceBefore = currentBalance,
            balanceAfter = balanceAfter,
            exerciseRewardAtTime = exerciseReward
        )

        val id = cashOutDao.insert(cashOut)
        return cashOut.copy(id = id)
    }

    /**
     * Get current balance from the check-in repository.
     */
    private suspend fun getCurrentBalance(): Double {
        return checkInRepository.getCurrentBalanceOnce()
    }

    /**
     * Get total amount cashed out all-time.
     */
    suspend fun getTotalCashedOut(): Double {
        return cashOutDao.getTotalCashedOut()
    }

    /**
     * Get all cash-outs.
     */
    suspend fun getAllCashOuts(): List<CashOut> {
        return cashOutDao.getAllCashOuts()
    }

    /**
     * Get count of rewards earned.
     */
    suspend fun getRewardCount(): Int {
        return cashOutDao.getCashOutCount()
    }

    /**
     * Calculate total workouts represented by all cash-outs.
     * Sums up workoutsToEarn from each individual cash-out to account
     * for potentially different reward amounts over time.
     */
    suspend fun getTotalWorkoutsRewarded(): Int {
        return getAllCashOuts().sumOf { it.workoutsToEarn }
    }
}

