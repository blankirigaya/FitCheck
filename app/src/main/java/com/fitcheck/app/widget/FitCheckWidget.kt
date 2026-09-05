package com.fitcheck.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.fitcheck.app.MainActivity
import com.fitcheck.app.R
import com.fitcheck.app.data.DataGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FitCheckWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Give the launcher a valid view immediately; otherwise it can remain on
        // its blank loading placeholder while Room loads wardrobe photos.
        ids.forEach { id ->
            val initial = RemoteViews(context.packageName, R.layout.widget_fitcheck)
            initial.setTextViewText(R.id.widget_title, "Everyday style for you")
            manager.updateAppWidget(id, initial)
        }
        val pending = goAsync()
        runBlocking(Dispatchers.IO) {
            val items = DataGraph.get(context).wardrobeRepository.getAvailableItems().filter { !it.imageUri.isNullOrBlank() }.take(6)
            ids.forEach { update(context, manager, it, items) }
        }
        pending.finish()
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int, items: List<com.fitcheck.app.data.local.entity.WardrobeItemEntity>) {
        val views = RemoteViews(context.packageName, R.layout.widget_fitcheck)
        val imageIds = intArrayOf(R.id.widget_image_1, R.id.widget_image_2, R.id.widget_image_3, R.id.widget_image_4, R.id.widget_image_5, R.id.widget_image_6)
        items.forEachIndexed { index, item -> BitmapFactory.decodeFile(item.imageUri)?.let { bitmap ->
            val scale = minOf(1f, 256f / maxOf(bitmap.width, bitmap.height).toFloat())
            val preview = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
            views.setImageViewBitmap(imageIds[index], preview)
        } }
        val dayLabel = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
        views.setTextViewText(R.id.widget_title, if (items.isEmpty()) "Add wardrobe photos" else "$dayLabel style for you")
        views.setOnClickPendingIntent(R.id.widget_root, android.app.PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(id, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) onUpdate(context, AppWidgetManager.getInstance(context), AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, javaClass)))
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FitCheckWidget::class.java))
            if (ids.isNotEmpty()) FitCheckWidget().onUpdate(context, manager, ids)
        }
    }
}
