package com.fitcheck.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fitcheck.app.MainActivity
import com.fitcheck.app.R

class FitCheckWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_fitcheck)
            views.setRemoteAdapter(R.id.widget_stack, Intent(context, FitCheckWidgetService::class.java).apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id) })
            views.setPendingIntentTemplate(R.id.widget_stack, PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_stack)
        }
    }
    override fun onReceive(context: Context, intent: Intent) { super.onReceive(context, intent); if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) refresh(context) }
    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FitCheckWidget::class.java))
            if (ids.isNotEmpty()) FitCheckWidget().onUpdate(context, manager, ids)
        }
    }
}
