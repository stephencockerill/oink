package com.oink.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oink.app.MainActivity
import com.oink.app.R

/**
 * Helper class for managing notifications.
 *
 * Why a helper class? Because notification code is verbose as fuck
 * and we don't want it cluttering up our other classes.
 *
 * Android notifications require:
 * 1. A notification channel (for Android 8.0+)
 * 2. Permission (for Android 13+)
 * 3. The actual notification with content
 */
object NotificationHelper {

    const val CHANNEL_ID = "oink_reminders"
    const val CHANNEL_NAME = "Daily Reminders"
    const val CHANNEL_DESCRIPTION = "Reminds you to fill your piggy bank"

    const val NOTIFICATION_ID_DAILY_REMINDER = 1001

    /**
     * Create the notification channel.
     * This MUST be called before showing any notifications.
     * Safe to call multiple times - it's a no-op if channel exists.
     */
    fun createNotificationChannel(context: Context) {
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            importance
        ).apply {
            description = CHANNEL_DESCRIPTION
            // Enable vibration for that extra nudge
            enableVibration(true)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Check if we have permission to post notifications.
     * Only needed for Android 13+ (API 33+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission not required before Android 13
            true
        }
    }

    /**
     * Show the daily reminder notification.
     */
    fun showDailyReminder(context: Context) {
        if (!hasNotificationPermission(context)) {
            // Can't show notification without permission
            return
        }

        // Intent to open the app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Add extra to indicate we came from notification
            putExtra(EXTRA_FROM_NOTIFICATION, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to feed the pig! 🐷")
            .setContentText("Did you exercise today? Add to your piggy bank!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Dismiss when tapped
            .build()

        // Show it
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID_DAILY_REMINDER,
            notification
        )
    }

    /**
     * Cancel the daily reminder notification.
     */
    fun cancelDailyReminder(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_DAILY_REMINDER)
    }

    const val EXTRA_FROM_NOTIFICATION = "from_notification"
}

