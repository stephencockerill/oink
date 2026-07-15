package com.oink.app.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptic feedback for the important, reward-bearing actions - a check-in and a
 * milestone unlock (docs/habit-psychology.md: "haptic feedback for important
 * actions").
 *
 * Haptics are about tactile confirmation, not motion, so they fire regardless of
 * the reduce-motion setting.
 *
 * Compose 1.7's `HapticFeedbackType` only exposes `LongPress`/`TextHandleMove`, so
 * feedback is performed through the platform [View.performHapticFeedback] with
 * [HapticFeedbackConstants]. The expressive `CONFIRM`/`REJECT` constants are API
 * 30+, so each has a pre-30 fallback that is present on every supported API.
 */
object Haptics {

    /** A positive confirmation - a successful/clean check-in. */
    fun confirm(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(constant)
    }

    /** A softer, non-punitive acknowledgement - a miss or a logged slip. */
    fun reject(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    /** A weightier tick for crossing into a new milestone tier. */
    fun milestone(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}
