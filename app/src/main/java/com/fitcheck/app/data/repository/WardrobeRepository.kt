package com.fitcheck.app.data.repository

import com.fitcheck.app.data.local.dao.WardrobeItemDao
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import kotlinx.coroutines.flow.Flow

/** Local wardrobe persistence. All operations are suspending or Flow-based. */
interface WardrobeRepository {
    fun observeAllItems(): Flow<List<WardrobeItemEntity>>
    suspend fun getAllItems(): List<WardrobeItemEntity>
    suspend fun getItemById(id: Long): WardrobeItemEntity?
    fun observeItemById(id: Long): Flow<WardrobeItemEntity?>
    fun observeByCategory(category: Category): Flow<List<WardrobeItemEntity>>
    fun observeAvailableItems(): Flow<List<WardrobeItemEntity>>
    suspend fun getAvailableItems(): List<WardrobeItemEntity>
    suspend fun insertItem(item: WardrobeItemEntity): Long
    suspend fun updateItem(item: WardrobeItemEntity)
    suspend fun deleteItem(item: WardrobeItemEntity)
    suspend fun deleteItemById(id: Long)
}

class RoomWardrobeRepository(
    private val dao: WardrobeItemDao
) : WardrobeRepository {

    override fun observeAllItems(): Flow<List<WardrobeItemEntity>> = dao.observeAll()

    override suspend fun getAllItems(): List<WardrobeItemEntity> = dao.getAll()

    override suspend fun getItemById(id: Long): WardrobeItemEntity? = dao.getById(id)

    override fun observeItemById(id: Long): Flow<WardrobeItemEntity?> = dao.observeById(id)

    override fun observeByCategory(category: Category): Flow<List<WardrobeItemEntity>> =
        dao.observeByCategory(category)

    override fun observeAvailableItems(): Flow<List<WardrobeItemEntity>> = dao.observeAvailable()

    override suspend fun getAvailableItems(): List<WardrobeItemEntity> = dao.getAvailable()

    override suspend fun insertItem(item: WardrobeItemEntity): Long {
        val now = System.currentTimeMillis()
        return dao.insert(item.copy(createdAt = now, updatedAt = now))
    }

    override suspend fun updateItem(item: WardrobeItemEntity) {
        dao.update(item.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteItem(item: WardrobeItemEntity) = dao.delete(item)

    override suspend fun deleteItemById(id: Long) = dao.deleteById(id)
}
