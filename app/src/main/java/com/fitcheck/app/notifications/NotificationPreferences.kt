package com.fitcheck.app.notifications

import android.content.Context

object NotificationPreferences {
    private const val FILE = "fitcheck_preferences"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_NOTIFICATIONS, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }
}
