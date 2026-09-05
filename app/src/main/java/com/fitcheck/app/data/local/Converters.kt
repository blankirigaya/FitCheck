package com.fitcheck.app.data.local

import androidx.room.TypeConverter
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.LaundryStatus

/**
 * Room type converters. String lists use the Unicode unit separator
 * (U+001F) as delimiter so commas inside values are safe and no JSON
 * library is needed.
 */
class Converters {

    @TypeConverter
    fun categoryToString(value: Category): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): Category = Category.valueOf(value)

    @TypeConverter
    fun laundryStatusToString(value: LaundryStatus): String = value.name

    @TypeConverter
    fun stringToLaundryStatus(value: String): LaundryStatus = LaundryStatus.valueOf(value)

    @TypeConverter
    fun stringListToString(values: List<String>): String =
        values.joinToString(separator = LIST_DELIMITER)

    @TypeConverter
    fun stringToStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(LIST_DELIMITER)

    companion object {
        const val LIST_DELIMITER = "\u001F"
    }
}
