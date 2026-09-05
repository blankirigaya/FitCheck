package com.fitcheck.app.data.local.entity

/** Top-level wardrobe categories. Subcategories stay as free-form strings so new ones can be added without a migration. */
enum class Category {
    TOP,
    BOTTOM,
    SHOES,
    OUTERWEAR,
    ACCESSORY
}

/** Laundry / wear state of a wardrobe item. */
enum class LaundryStatus {
    CLEAN,
    WORN,
    IN_LAUNDRY
}
