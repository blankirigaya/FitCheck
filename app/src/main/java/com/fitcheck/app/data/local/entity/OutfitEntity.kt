package com.fitcheck.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A saved outfit: a named set of wardrobe items plus optional AI explanation. */
@Entity(tableName = "outfits")
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** Epoch millis. Stamped by the repository on save. */
    val createdAt: Long = 0L,
    val generatedByAI: Boolean = false,
    val explanation: String? = null,
    val occasion: String? = null,
    val contextSummary: String? = null
)

/**
 * Junction table for the many-to-many Outfit ↔ WardrobeItem relationship.
 * Deleting either side removes the link rows (CASCADE on both).
 */
@Entity(
    tableName = "outfit_items",
    primaryKeys = ["outfitId", "wardrobeItemId"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WardrobeItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["wardrobeItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("wardrobeItemId")]
)
data class OutfitItemCrossRef(
    val outfitId: Long,
    val wardrobeItemId: Long
)

/** An outfit together with its items, loaded transactionally. */
data class OutfitWithItems(
    @Embedded val outfit: OutfitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            OutfitItemCrossRef::class,
            parentColumn = "outfitId",
            entityColumn = "wardrobeItemId"
        )
    )
    val items: List<WardrobeItemEntity>
)
