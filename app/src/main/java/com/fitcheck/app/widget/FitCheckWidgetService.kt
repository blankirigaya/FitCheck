package com.fitcheck.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.fitcheck.app.R
import com.fitcheck.app.data.DataGraph
import com.fitcheck.app.data.PexelsImageSearch
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URL
import kotlinx.coroutines.runBlocking

class FitCheckWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: android.content.Context) : RemoteViewsFactory {
        private var items: List<WardrobeItemEntity> = emptyList()
        override fun onCreate() = Unit
        override fun onDestroy() = Unit
        override fun getCount(): Int = 2
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true
        override fun getLoadingView(): RemoteViews? = null
        override fun onDataSetChanged() { items = runBlocking { DataGraph.get(context).wardrobeRepository.getAvailableItems() } }

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_page)
            val imageIds = intArrayOf(R.id.widget_image_1, R.id.widget_image_2, R.id.widget_image_3, R.id.widget_image_4, R.id.widget_image_5, R.id.widget_image_6)
            if (position == 0) {
                val photos = items.filter { !it.imageUri.isNullOrBlank() }.take(6)
                val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
                views.setTextViewText(R.id.widget_page_title, if (photos.isEmpty()) "Dress Me" else "$day style for you")
                views.setTextViewText(R.id.widget_page_subtitle, "Dress Me Today")
                photos.forEachIndexed { index, item -> BitmapFactory.decodeFile(item.imageUri)?.let { views.setImageViewBitmap(imageIds[index], preview(it)) } }
                views.setTextViewText(R.id.widget_page_body, if (photos.isEmpty()) "Add wardrobe photos" else "Swipe left for what to buy next")
                views.setTextViewText(R.id.widget_page_indicator, "●  ○")
            } else {
                views.setViewVisibility(R.id.widget_images, View.GONE)
                views.setViewVisibility(R.id.widget_purchase_list, View.VISIBLE)
                val purchases = topPurchases(items)
                views.setTextViewText(R.id.widget_page_title, "What to buy next")
                views.setTextViewText(R.id.widget_page_subtitle, "Top 3 highest-impact additions")
                purchases.forEachIndexed { index, purchase ->
                    val image = runCatching { PexelsImageSearch.searchProductImage(purchase.title)?.let { URL(it).openStream().use(BitmapFactory::decodeStream) } }.getOrNull()
                    if (image != null) views.setImageViewBitmap(intArrayOf(R.id.purchase_image_1, R.id.purchase_image_2, R.id.purchase_image_3)[index], preview(image))
                    views.setTextViewText(intArrayOf(R.id.purchase_title_1, R.id.purchase_title_2, R.id.purchase_title_3)[index], "${index + 1}. ${purchase.title}")
                    views.setTextViewText(intArrayOf(R.id.purchase_detail_1, R.id.purchase_detail_2, R.id.purchase_detail_3)[index], "+${purchase.outfits} complete sets • ${purchase.priority}")
                }
                views.setTextViewText(R.id.widget_page_body, "Swipe right for Dress Me")
                views.setTextViewText(R.id.widget_page_indicator, "○  ●")
            }
            return views
        }

        private fun preview(bitmap: Bitmap): Bitmap {
            val scale = minOf(1f, 256f / maxOf(bitmap.width, bitmap.height).toFloat())
            return if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        }

        private data class Purchase(val title: String, val outfits: Int, val priority: String, val reason: String)
        private fun topPurchases(items: List<WardrobeItemEntity>): List<Purchase> {
            val tops = items.count { it.category == Category.TOP }
            val bottoms = items.count { it.category == Category.BOTTOM }
            val shoes = items.count { it.category == Category.SHOES }
            return listOf(
                Purchase("A versatile shirt", bottoms * shoes, "TOP", "Adds the most complete looks from your wardrobe."),
                Purchase("Everyday trousers", tops * shoes, "BOTTOM", "Pairs with your existing tops and shoes."),
                Purchase("Versatile sneakers", tops * bottoms, "SHOES", "Completes more combinations across your wardrobe."),
                Purchase("Lightweight jacket", tops * bottoms * shoes, "OUTERWEAR", "Adds layering options to complete looks."),
                Purchase("Everyday accessory", tops * bottoms * shoes, "ACCESSORY", "Adds variety to complete outfits.")
            ).sortedByDescending { it.outfits }.take(3)
        }
    }
}
