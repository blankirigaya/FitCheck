package com.fitcheck.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fitcheck.app.data.local.dao.OutfitDao
import com.fitcheck.app.data.local.dao.StylePreferenceDao
import com.fitcheck.app.data.local.dao.WardrobeItemDao
import com.fitcheck.app.data.local.dao.WearEventDao
import com.fitcheck.app.data.local.entity.OutfitEntity
import com.fitcheck.app.data.local.entity.OutfitItemCrossRef
import com.fitcheck.app.data.local.entity.StylePreferenceEntity
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import com.fitcheck.app.data.local.entity.WearEventEntity

/**
 * Local Room database for the personal wardrobe. Everything stays on-device;
 * there is intentionally no network repository or sync.
 */
@Database(
    entities = [
        WardrobeItemEntity::class,
        WearEventEntity::class,
        OutfitEntity::class,
        OutfitItemCrossRef::class,
        StylePreferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FitCheckDatabase : RoomDatabase() {

    abstract fun wardrobeItemDao(): WardrobeItemDao
    abstract fun wearEventDao(): WearEventDao
    abstract fun outfitDao(): OutfitDao
    abstract fun stylePreferenceDao(): StylePreferenceDao

    companion object {
        private const val DB_NAME = "fitcheck.db"

        fun open(context: Context): FitCheckDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FitCheckDatabase::class.java,
                DB_NAME
            ).build()

        /** In-memory instance for instrumentation tests. Allows main-thread queries for test simplicity. */
        fun openInMemory(context: Context): FitCheckDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                FitCheckDatabase::class.java
            ).allowMainThreadQueries().build()
    }
}
