package com.fitcheck.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitcheck.app.data.local.FitCheckDatabase
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.LaundryStatus
import com.fitcheck.app.data.local.entity.OutfitEntity
import com.fitcheck.app.data.local.entity.StylePreferenceEntity
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import com.fitcheck.app.data.repository.RoomOutfitRepository
import com.fitcheck.app.data.repository.RoomStylePreferenceRepository
import com.fitcheck.app.data.repository.RoomWardrobeRepository
import com.fitcheck.app.data.repository.RoomWearRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2 acceptance test against a real (in-memory) Room database:
 * insert → read → update → wear event → wear history → outfit with items
 * → read outfit → delete.
 */
@RunWith(AndroidJUnit4::class)
class WardrobeDatabaseTest {

    private lateinit var db: FitCheckDatabase
    private lateinit var wardrobe: RoomWardrobeRepository
    private lateinit var wear: RoomWearRepository
    private lateinit var outfits: RoomOutfitRepository
    private lateinit var style: RoomStylePreferenceRepository

    @Before
    fun openDb() {
        db = FitCheckDatabase.openInMemory(ApplicationProvider.getApplicationContext())
        wardrobe = RoomWardrobeRepository(db.wardrobeItemDao())
        wear = RoomWearRepository(db)
        outfits = RoomOutfitRepository(db.outfitDao())
        style = RoomStylePreferenceRepository(db.stylePreferenceDao())
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun item(name: String, category: Category) = WardrobeItemEntity(
        name = name,
        category = category,
        subcategory = "t-shirt",
        color = "BLACK",
        styleTags = listOf("CASUAL", "MINIMAL")
    )

    @Test
    fun wardrobeCrud() = runBlocking {
        // Insert a test wardrobe item.
        val id = wardrobe.insertItem(item("Black Tee", Category.TOP))
        assertTrue(id > 0L)

        // Read it back.
        val read = wardrobe.getItemById(id)
        assertNotNull(read)
        assertEquals("Black Tee", read!!.name)
        assertEquals(Category.TOP, read.category)
        assertEquals(listOf("CASUAL", "MINIMAL"), read.styleTags)

        // Update it.
        wardrobe.updateItem(read.copy(name = "Black Tee Updated", isAvailable = false))
        assertEquals("Black Tee Updated", wardrobe.getItemById(id)!!.name)

        // Delete it.
        wardrobe.deleteItemById(id)
        assertNull(wardrobe.getItemById(id))
    }

    @Test
    fun wearHistory() = runBlocking {
        val id = wardrobe.insertItem(item("Blue Jeans", Category.BOTTOM))

        // Insert a wear event.
        val wornAt = 1_700_000_000_000L
        val eventId = wear.recordWear(id, wornAt = wornAt, occasion = "CASUAL")
        assertTrue(eventId > 0L)

        // Query recent wear history.
        val recent = wear.getRecentWearEvents(10)
        assertEquals(1, recent.size)
        assertEquals(id, recent[0].wardrobeItemId)
        assertEquals(wornAt, recent[0].wornAt)

        // History for the item.
        assertEquals(1, wear.getWearHistoryForItem(id).size)

        // Recording a wear bumps counters on the item.
        val updated = wardrobe.getItemById(id)!!
        assertEquals(1, updated.wearCount)
        assertEquals(wornAt, updated.lastWorn)

        // Recently-worn lookup used by candidate selection.
        assertEquals(listOf(id), wear.getRecentlyWornItemIds(wornAt - 1L))
        assertTrue(wear.getRecentlyWornItemIds(wornAt + 1L).isEmpty())
    }

    @Test
    fun outfitWithItems() = runBlocking {
        val topId = wardrobe.insertItem(item("White Shirt", Category.TOP))
        val bottomId = wardrobe.insertItem(item("Beige Chinos", Category.BOTTOM))
        val shoeId = wardrobe.insertItem(item("White Sneakers", Category.SHOES))

        // Save an outfit with multiple wardrobe items.
        val outfitId = outfits.saveOutfit(
            OutfitEntity(name = "Casual Friday", createdAt = 0L, generatedByAI = true, explanation = "test"),
            listOf(topId, bottomId, shoeId)
        )
        assertTrue(outfitId > 0L)

        // Read the outfit and its items.
        val loaded = outfits.getOutfit(outfitId)
        assertNotNull(loaded)
        assertEquals("Casual Friday", loaded!!.outfit.name)
        assertEquals(
            setOf(topId, bottomId, shoeId),
            loaded.items.map { it.id }.toSet()
        )

        // History observation emits the saved outfit.
        val history = outfits.observeOutfitHistory().first()
        assertEquals(1, history.size)

        // Deleting an unused item leaves the outfit intact.
        val spareId = wardrobe.insertItem(item("Spare Belt", Category.ACCESSORY))
        wardrobe.deleteItemById(spareId)
        assertEquals(3, outfits.getOutfit(outfitId)!!.items.size)
    }

    @Test
    fun stylePreferences() = runBlocking {
        // Defaults when nothing stored.
        assertEquals(emptyList<String>(), style.readPreferences().preferredColors)

        style.updatePreferences(
            StylePreferenceEntity(
                preferredColors = listOf("BLACK", "WHITE"),
                dislikedColors = listOf("NEON"),
                preferredStyles = listOf("MINIMAL")
            )
        )
        val read = style.readPreferences()
        assertEquals(listOf("BLACK", "WHITE"), read.preferredColors)
        assertEquals(listOf("MINIMAL"), read.preferredStyles)
        assertEquals(listOf("BLACK", "WHITE"), style.observePreferences().first().preferredColors)
    }
}
