package com.fitcheck.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fitcheck.app.data.local.entity.WearEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WearEventDao {

    @Insert
    suspend fun insert(event: WearEventEntity): Long

    @Query("SELECT * FROM wear_events ORDER BY wornAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WearEventEntity>>

    @Query("SELECT * FROM wear_events ORDER BY wornAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<WearEventEntity>

    @Query("SELECT * FROM wear_events WHERE wardrobeItemId = :itemId ORDER BY wornAt DESC")
    fun observeForItem(itemId: Long): Flow<List<WearEventEntity>>

    @Query("SELECT * FROM wear_events WHERE wardrobeItemId = :itemId ORDER BY wornAt DESC")
    suspend fun getForItem(itemId: Long): List<WearEventEntity>

    @Query("SELECT COUNT(*) FROM wear_events WHERE wardrobeItemId = :itemId")
    suspend fun countForItem(itemId: Long): Int
}
