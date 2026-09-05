package com.fitcheck.app

import com.fitcheck.app.data.local.Converters
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.LaundryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `category round-trips through string`() {
        for (category in Category.entries) {
            assertEquals(category, converters.stringToCategory(converters.categoryToString(category)))
        }
    }

    @Test
    fun `laundry status round-trips through string`() {
        for (status in LaundryStatus.entries) {
            assertEquals(status, converters.stringToLaundryStatus(converters.laundryStatusToString(status)))
        }
    }

    @Test
    fun `string list round-trips`() {
        val tags = listOf("CASUAL", "MINIMAL", "SUMMER")
        assertEquals(tags, converters.stringToStringList(converters.stringListToString(tags)))
    }

    @Test
    fun `empty list round-trips to empty`() {
        assertEquals(emptyList<String>(), converters.stringToStringList(converters.stringListToString(emptyList())))
    }

    @Test
    fun `list values containing commas survive round-trip`() {
        val tags = listOf("BLUE, LIGHT", "RED")
        assertEquals(tags, converters.stringToStringList(converters.stringListToString(tags)))
    }

    @Test
    fun `single element list round-trips`() {
        val tags = listOf("CASUAL")
        assertEquals(tags, converters.stringToStringList(converters.stringListToString(tags)))
    }
}
