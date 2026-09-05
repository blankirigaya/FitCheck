package com.fitcheck.app.data.repository

import androidx.room.withTransaction
import com.fitcheck.app.data.local.FitCheckDatabase
import com.fitcheck.app.data.local.entity.WearEventEntity
import kotlinx.coroutines.flow.Flow

/** Wear-history persistence. Recording a wear also bumps the item's counters atomically. */
interface WearRepository {
    suspend fun recordWear(itemId: Long, wornAt: Long = System.currentTimeMillis(), occasion: String? = null, outfitId: Long? = null): Long
    fun observeRecentWearEvents(limit: Int): Flow<List<WearEventEntity>>
    suspend fun getRecentWearEvents(limit: Int): List<WearEventEntity>
    fun observeWearHistoryForItem(itemId: Long): Flow<List<WearEventEntity>>
    suspend fun getWearHistoryForItem(itemId: Long): List<WearEventEntity>
    suspend fun getRecentlyWornItemIds(since: Long): List<Long>
}

class RoomWearRepository(
    private val db: FitCheckDatabase
) : WearRepository {

    override suspend fun recordWear(itemId: Long, wornAt: Long, occasion: String?, outfitId: Long?): Long {
        var eventId = 0L
        db.withTransaction {
            eventId = db.wearEventDao().insert(
                WearEventEntity(
                    wardrobeItemId = itemId,
                    outfitId = outfitId,
                    wornAt = wornAt,
                    occasion = occasion
                )
            )
            db.wardrobeItemDao().bumpWearStats(itemId, wornAt)
        }
        return eventId
    }

    override fun observeRecentWearEvents(limit: Int): Flow<List<WearEventEntity>> =
        db.wearEventDao().observeRecent(limit)

    override suspend fun getRecentWearEvents(limit: Int): List<WearEventEntity> =
        db.wearEventDao().getRecent(limit)

    override fun observeWearHistoryForItem(itemId: Long): Flow<List<WearEventEntity>> =
        db.wearEventDao().observeForItem(itemId)

    override suspend fun getWearHistoryForItem(itemId: Long): List<WearEventEntity> =
        db.wearEventDao().getForItem(itemId)

    override suspend fun getRecentlyWornItemIds(since: Long): List<Long> =
        db.wardrobeItemDao().getWornItemIdsSince(since)
}
