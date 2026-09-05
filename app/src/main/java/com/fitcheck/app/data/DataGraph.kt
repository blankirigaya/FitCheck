package com.fitcheck.app.data

import android.content.Context
import com.fitcheck.app.data.local.FitCheckDatabase
import com.fitcheck.app.data.repository.OutfitRepository
import com.fitcheck.app.data.repository.RoomOutfitRepository
import com.fitcheck.app.data.repository.RoomStylePreferenceRepository
import com.fitcheck.app.data.repository.RoomWardrobeRepository
import com.fitcheck.app.data.repository.RoomWearRepository
import com.fitcheck.app.data.repository.StylePreferenceRepository
import com.fitcheck.app.data.repository.WardrobeRepository
import com.fitcheck.app.data.repository.WearRepository

/**
 * Manual service graph for local data, mirroring [com.fitcheck.app.ai.AiRuntimeProvider].
 * Everything here is on-device; there is no network repository.
 */
class DataGraph private constructor(context: Context) {

    private val database: FitCheckDatabase = FitCheckDatabase.open(context)

    val wardrobeRepository: WardrobeRepository =
        RoomWardrobeRepository(database.wardrobeItemDao())

    val wearRepository: WearRepository =
        RoomWearRepository(database)

    val outfitRepository: OutfitRepository =
        RoomOutfitRepository(database.outfitDao())

    val stylePreferenceRepository: StylePreferenceRepository =
        RoomStylePreferenceRepository(database.stylePreferenceDao())

    companion object {
        @Volatile private var instance: DataGraph? = null

        fun get(context: Context): DataGraph {
            return instance ?: synchronized(this) {
                instance ?: DataGraph(context.applicationContext).also { instance = it }
            }
        }
    }
}
