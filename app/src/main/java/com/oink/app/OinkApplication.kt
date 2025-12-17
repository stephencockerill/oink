package com.oink.app

import android.app.Application
import com.oink.app.data.AppDatabase
import com.oink.app.notifications.NotificationHelper

/**
 * Application class for Oink.
 * Initializes the database and provides application-wide dependencies.
 */
class OinkApplication : Application() {

    /**
     * Lazy initialization of the Room database.
     * The database instance is created only when first accessed.
     */
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Create notification channel on app start
        // This is safe to call multiple times - Android ignores duplicates
        NotificationHelper.createNotificationChannel(this)
    }
}

