package com.fitcheck.app.data.repository

import com.fitcheck.app.data.local.dao.StylePreferenceDao
import com.fitcheck.app.data.local.entity.StylePreferenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Structured style-preference persistence (single row). */
interface StylePreferenceRepository {
    fun observePreferences(): Flow<StylePreferenceEntity>
    suspend fun readPreferences(): StylePreferenceEntity
    suspend fun updatePreferences(preferences: StylePreferenceEntity)
}

class RoomStylePreferenceRepository(
    private val dao: StylePreferenceDao
) : StylePreferenceRepository {

    override fun observePreferences(): Flow<StylePreferenceEntity> =
        dao.observe().map { it ?: StylePreferenceEntity() }

    override suspend fun readPreferences(): StylePreferenceEntity =
        dao.get() ?: StylePreferenceEntity()

    override suspend fun updatePreferences(preferences: StylePreferenceEntity) {
        dao.upsert(
            preferences.copy(
                id = StylePreferenceEntity.SINGLETON_ID,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
