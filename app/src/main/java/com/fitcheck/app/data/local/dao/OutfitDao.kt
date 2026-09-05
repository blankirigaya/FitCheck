package com.fitcheck.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.fitcheck.app.data.local.entity.OutfitEntity
import com.fitcheck.app.data.local.entity.OutfitItemCrossRef
import com.fitcheck.app.data.local.entity.OutfitWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface OutfitDao {

    @Insert
    suspend fun insertOutfit(outfit: OutfitEntity): Long

    @Insert
    suspend fun insertCrossRefs(refs: List<OutfitItemCrossRef>)

    /** Save an outfit together with its item links. Returns the outfit id. */
    @Transaction
    suspend fun saveOutfitWithItems(outfit: OutfitEntity, itemIds: List<Long>): Long {
        val outfitId = insertOutfit(outfit)
        insertCrossRefs(itemIds.distinct().map { OutfitItemCrossRef(outfitId, it) })
        return outfitId
    }

    @Transaction
    @Query("SELECT * FROM outfits WHERE id = :id")
    suspend fun getOutfitWithItems(id: Long): OutfitWithItems?

    @Transaction
    @Query("SELECT * FROM outfits ORDER BY createdAt DESC")
    fun observeOutfitHistory(): Flow<List<OutfitWithItems>>

    @Transaction
    @Query("SELECT * FROM outfits ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentOutfits(limit: Int): List<OutfitWithItems>

    @Query("DELETE FROM outfits WHERE id = :id")
    suspend fun deleteById(id: Long)
}
