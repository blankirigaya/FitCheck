package com.fitcheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Structured style signals for one user, stored as discrete string lists
 * (not a JSON blob) so Gemma prompts and Kotlin filters can consume each
 * signal independently. Single-row table: [id] is always [SINGLETON_ID].
 */
@Entity(tableName = "style_preferences")
data class StylePreferenceEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val preferredColors: List<String> = emptyList(),
    val dislikedColors: List<String> = emptyList(),
    val preferredFits: List<String> = emptyList(),
    val preferredStyles: List<String> = emptyList(),
    val preferredOccasions: List<String> = emptyList(),
    /** Epoch millis of the last edit. */
    val updatedAt: Long = 0L
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
