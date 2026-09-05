package com.fitcheck.app

import com.fitcheck.app.capsule.CapsuleAnalyzer
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleAnalyzerTest {
    private val analyzer = CapsuleAnalyzer()
    private fun item(id: Long, name: String, category: Category, color: String, style: String = "CASUAL", wearCount: Int = 0) =
        WardrobeItemEntity(id = id, name = name, category = category, color = color, style = style, wearCount = wearCount)

    @Test fun `empty wardrobe returns safe zero analysis`() {
        val result = analyzer.analyze(emptyList())
        assertEquals(0, result.capsuleScore)
        assertEquals(0, result.outfitPotential)
        assertTrue(result.insight.contains("Add clothing"))
    }

    @Test fun `capsule selects core categories and counts compatible outfits`() {
        val wardrobe = listOf(item(1, "Black shirt", Category.TOP, "black"), item(2, "Beige chinos", Category.BOTTOM, "beige"), item(3, "White sneakers", Category.SHOES, "white"))
        val result = analyzer.analyze(wardrobe)
        assertEquals(3, result.capsuleItems.size)
        assertEquals(1, result.outfitPotential)
        assertEquals(100, result.categoryCoverage)
    }

    @Test fun `similar items are grouped as overlapping without deletion`() {
        val wardrobe = listOf(item(1, "Blue shirt", Category.TOP, "blue"), item(2, "Blue oxford", Category.TOP, "blue"), item(3, "Khaki pants", Category.BOTTOM, "khaki"), item(4, "White sneakers", Category.SHOES, "white"))
        val result = analyzer.analyze(wardrobe)
        assertEquals(1, result.redundancyGroups.size)
        assertEquals(2, result.redundancyGroups.first().items.size)
    }

    @Test fun `items in laundry are excluded from capsule`() {
        val wardrobe = listOf(item(1, "Black shirt", Category.TOP, "black"), item(2, "Beige chinos", Category.BOTTOM, "beige"), item(3, "White sneakers", Category.SHOES, "white").copy(isAvailable = false))
        val result = analyzer.analyze(wardrobe)
        assertTrue(result.gaps.any { it.category == Category.SHOES })
    }
}
