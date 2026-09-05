package com.fitcheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single clothing item owned by the user. All data stays on-device.
 *
 * Free-form string columns (subcategory, color, material, fit, size, brand)
 * are deliberately not enums so Gemma-extracted values and future categories
 * can be stored without a schema migration.
 */
@Entity(
    tableName = "wardrobe_items",
    indices = [
        Index("category"),
        Index("isAvailable"),
        Index("lastWorn")
    ]
)
data class WardrobeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val category: Category,
    val subcategory: String? = null,
    val color: String? = null,
    val secondaryColor: String? = null,
    val material: String? = null,
    val fit: String? = null,
    /** Primary style tag, e.g. CASUAL. */
    val style: String? = null,
    /** Extra style tags from attribute extraction, e.g. [CASUAL, MINIMAL]. */
    val styleTags: List<String> = emptyList(),
    val brand: String? = null,
    val size: String? = null,
    /** Local content URI / file path of the item photo. Never uploaded. */
    val imageUri: String? = null,
    val purchasePrice: Double? = null,
    /** Epoch millis. */
    val purchaseDate: Long? = null,
    val wearCount: Int = 0,
    /** Epoch millis of the most recent wear. */
    val lastWorn: Long? = null,
    val laundryStatus: LaundryStatus = LaundryStatus.CLEAN,
    val isAvailable: Boolean = true,
    /** 1 (very casual) .. 5 (very formal), when known. */
    val formality: Int? = null,
    /** Season suitability tags, e.g. [SUMMER, MONSOON]. */
    val seasonTags: List<String> = emptyList(),
    /** Epoch millis. Stamped by the repository on insert. */
    val createdAt: Long = 0L,
    /** Epoch millis. Stamped by the repository on insert/update. */
    val updatedAt: Long = 0L
)
