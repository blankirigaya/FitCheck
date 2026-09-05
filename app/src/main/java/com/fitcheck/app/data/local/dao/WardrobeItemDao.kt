package com.fitcheck.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WardrobeItemDao {

    @Query("SELECT * FROM wardrobe_items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WardrobeItemEntity>>

    @Query("SELECT * FROM wardrobe_items ORDER BY updatedAt DESC")
    suspend fun getAll(): List<WardrobeItemEntity>

    @Query("SELECT * FROM wardrobe_items WHERE id = :id")
    suspend fun getById(id: Long): WardrobeItemEntity?

    @Query("SELECT * FROM wardrobe_items WHERE id = :id")
    fun observeById(id: Long): Flow<WardrobeItemEntity?>

    @Query("SELECT * FROM wardrobe_items WHERE category = :category ORDER BY updatedAt DESC")
    fun observeByCategory(category: Category): Flow<List<WardrobeItemEntity>>

    @Query("SELECT * FROM wardrobe_items WHERE isAvailable = 1 ORDER BY updatedAt DESC")
    fun observeAvailable(): Flow<List<WardrobeItemEntity>>

    @Query("SELECT * FROM wardrobe_items WHERE isAvailable = 1 ORDER BY updatedAt DESC")
    suspend fun getAvailable(): List<WardrobeItemEntity>

    @Query("SELECT id FROM wardrobe_items WHERE lastWorn IS NOT NULL AND lastWorn >= :since ORDER BY lastWorn DESC")
    suspend fun getWornItemIdsSince(since: Long): List<Long>

    @Insert
    suspend fun insert(item: WardrobeItemEntity): Long

    @Update
    suspend fun update(item: WardrobeItemEntity)

    @Query("UPDATE wardrobe_items SET wearCount = wearCount + 1, lastWorn = :wornAt, updatedAt = :wornAt WHERE id = :id")
    suspend fun bumpWearStats(id: Long, wornAt: Long)

    @Delete
    suspend fun delete(item: WardrobeItemEntity)

    @Query("DELETE FROM wardrobe_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
