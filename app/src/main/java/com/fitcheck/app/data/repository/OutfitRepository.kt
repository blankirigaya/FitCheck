package com.fitcheck.app.data.repository

import com.fitcheck.app.data.local.dao.OutfitDao
import com.fitcheck.app.data.local.entity.OutfitEntity
import com.fitcheck.app.data.local.entity.OutfitWithItems
import kotlinx.coroutines.flow.Flow

/** Saved-outfit persistence, including outfit ↔ item links. */
interface OutfitRepository {
    suspend fun saveOutfit(outfit: OutfitEntity, itemIds: List<Long>): Long
    suspend fun getOutfit(outfitId: Long): OutfitWithItems?
    fun observeOutfitHistory(): Flow<List<OutfitWithItems>>
    suspend fun getRecentOutfits(limit: Int): List<OutfitWithItems>
    suspend fun deleteOutfit(outfitId: Long)
}

class RoomOutfitRepository(
    private val dao: OutfitDao
) : OutfitRepository {

    override suspend fun saveOutfit(outfit: OutfitEntity, itemIds: List<Long>): Long {
        val now = System.currentTimeMillis()
        return dao.saveOutfitWithItems(outfit.copy(createdAt = now), itemIds)
    }

    override suspend fun getOutfit(outfitId: Long): OutfitWithItems? =
        dao.getOutfitWithItems(outfitId)

    override fun observeOutfitHistory(): Flow<List<OutfitWithItems>> =
        dao.observeOutfitHistory()

    override suspend fun getRecentOutfits(limit: Int): List<OutfitWithItems> =
        dao.getRecentOutfits(limit)

    override suspend fun deleteOutfit(outfitId: Long) = dao.deleteById(outfitId)
}
