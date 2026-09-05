package com.fitcheck.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fitcheck.app.data.local.entity.StylePreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StylePreferenceDao {

    @Query("SELECT * FROM style_preferences WHERE id = 1")
    fun observe(): Flow<StylePreferenceEntity?>

    @Query("SELECT * FROM style_preferences WHERE id = 1")
    suspend fun get(): StylePreferenceEntity?

    @Upsert
    suspend fun upsert(preferences: StylePreferenceEntity)
}
