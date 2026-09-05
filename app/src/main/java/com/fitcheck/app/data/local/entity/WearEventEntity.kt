package com.fitcheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records that a wardrobe item was worn at a point in time.
 *
 * Deleting an item removes its wear history (CASCADE). Deleting an outfit
 * keeps the wear event but clears the link (SET NULL) so history survives.
 */
@Entity(
    tableName = "wear_events",
    foreignKeys = [
        ForeignKey(
            entity = WardrobeItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["wardrobeItemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["outfitId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("wardrobeItemId"),
        Index("outfitId"),
        Index("wornAt")
    ]
)
data class WearEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val wardrobeItemId: Long,
    val outfitId: Long? = null,
    /** Epoch millis. */
    val wornAt: Long,
    val occasion: String? = null
)
