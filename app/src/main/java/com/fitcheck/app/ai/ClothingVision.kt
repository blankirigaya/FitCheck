package com.fitcheck.app.ai

import com.fitcheck.app.data.local.entity.Category

data class ClothingAttributes(
    val name: String,
    val category: Category,
    val subcategory: String?,
    val color: String?,
    val material: String?,
    val fit: String?,
    val style: String?,
    val formality: Int?
)

object ClothingVisionParser {
    fun parse(raw: String): ClothingAttributes? {
        fun value(key: String) = Regex("\\\"$key\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.trim()
        val category = when (value("category")?.uppercase()) {
            "TOP", "SHIRT", "JACKET", "SWEATER", "OUTERWEAR" -> Category.TOP
            "BOTTOM", "PANTS", "TROUSERS", "SHORTS", "SKIRT" -> Category.BOTTOM
            "SHOES", "SHOE", "FOOTWEAR" -> Category.SHOES
            "ACCESSORY", "ACCESSORIES" -> Category.ACCESSORY
            else -> return null
        }
        val name = value("name")?.ifBlank { null } ?: return null
        val formality = value("formality")?.toIntOrNull()?.coerceIn(1, 5)
        return ClothingAttributes(name, category, value("subcategory"), value("color"), value("material"), value("fit"), value("style"), formality)
    }
}
