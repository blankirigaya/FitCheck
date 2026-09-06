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
        // Gemma may wrap JSON in a markdown fence or add a short sentence
        // before it. Keep extraction deliberately tolerant so a usable vision
        // result is not discarded just because the formatting is imperfect.
        val cleaned = raw.replace("```json", "", ignoreCase = true).replace("```", "").trim()
        fun value(key: String): String? {
            val quoted = Regex("[\\\"']$key[\\\"']\\s*:\\s*[\\\"']([^\\\"']*)[\\\"']", RegexOption.IGNORE_CASE)
                .find(cleaned)?.groupValues?.get(1)?.trim()
            if (!quoted.isNullOrBlank()) return quoted
            return Regex("[\\\"']$key[\\\"']\\s*:\\s*([^,}\\n]+)", RegexOption.IGNORE_CASE)
                .find(cleaned)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
        }
        val detectedName = value("name")?.ifBlank { null } ?: return null
        val detectedSubcategory = value("subcategory")
        val category = categoryFromAttributes(
            value("category"),
            detectedName,
            detectedSubcategory
        ) ?: return null
        val formality = value("formality")?.toIntOrNull()?.coerceIn(1, 5)
        return ClothingAttributes(detectedName, category, detectedSubcategory, value("color"), value("material"), value("fit"), value("style"), formality)
    }

    private fun categoryFromAttributes(rawCategory: String?, name: String, subcategory: String?): Category? {
        val searchable = "$name ${subcategory.orEmpty()}".uppercase()
        fun containsAny(vararg terms: String) = terms.any { searchable.contains(it) }
        // Correct common model mistakes before accepting its generic category.
        if (containsAny("PANTS", "TROUSER", "JEANS", "CHINOS", "SHORTS", "SKIRT", "BOTTOM", "DHOTI", "SALWAR")) return if (containsAny("DHOTI", "SALWAR")) Category.ETHNIC_WEAR else Category.BOTTOM
        if (containsAny("GLASSES", "EYEWEAR", "SUNGLASSES", "SPECTACLE", "WATCH", "BELT", "BAG", "PURSE", "HAT", "CAP", "SCARF", "JEWELRY", "JEWELLERY", "NECKLACE", "BRACELET", "BANGLE", "WRISTBAND")) return Category.ACCESSORY
        if (containsAny("KURTA", "KURTI", "SAREE", "SARI", "SHERWANI", "LEHENGA", "ETHNIC", "TRADITIONAL", "SALWAR")) return Category.ETHNIC_WEAR
        if (containsAny("JACKET", "COAT", "BLAZER", "CARDIGAN", "TRENCH", "OUTERWEAR", "OVERSHIRT")) return Category.OUTERWEAR
        return when (rawCategory?.uppercase()) {
            "TOP", "SHIRT", "T-SHIRT", "BLOUSE", "SWEATER", "HOODIE" -> Category.TOP
            "BOTTOM", "PANTS", "TROUSERS", "SHORTS", "SKIRT" -> Category.BOTTOM
            "SHOES", "SHOE", "FOOTWEAR" -> Category.SHOES
            "OUTERWEAR", "JACKET", "COAT", "BLAZER", "CARDIGAN", "TRENCHCOAT" -> Category.OUTERWEAR
            "ACCESSORY", "ACCESSORIES", "GLASSES", "EYEWEAR", "SUNGLASSES", "WATCH", "BELT", "BAG", "HAT", "JEWELRY", "NECKLACE", "BRACELET", "BANGLE", "WRISTBAND" -> Category.ACCESSORY
            "ETHNIC", "ETHNIC_WEAR", "TRADITIONAL", "KURTA", "KURTI", "SAREE", "SARI", "SHERWANI", "LEHENGA", "DHOTI" -> Category.ETHNIC_WEAR
            else -> null
        }
    }
}
