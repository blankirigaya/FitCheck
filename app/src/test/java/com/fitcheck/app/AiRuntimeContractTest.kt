package com.fitcheck.app

import com.fitcheck.app.ai.Accelerator
import com.fitcheck.app.ai.InitState
import com.fitcheck.app.ai.ModelInfo
import com.fitcheck.app.ai.RuntimeSnapshot
import com.fitcheck.app.ai.ClothingVisionParser
import com.fitcheck.app.data.local.entity.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRuntimeContractTest {

    @Test
    fun `ModelInfo Missing exposes zero size and empty path`() {
        assertEquals(0L, ModelInfo.Missing.sizeBytes)
        assertEquals("", ModelInfo.Missing.absolutePath)
        assertEquals("No .litertlm model found", ModelInfo.Missing.displayName)
    }

    @Test
    fun `Accelerator labels are uppercase`() {
        assertEquals("CPU", Accelerator.CPU.label)
        assertEquals("GPU", Accelerator.GPU.label)
        assertEquals("NPU", Accelerator.NPU.label)
    }

    @Test
    fun `Ready init state reports the active accelerator`() {
        val ready = InitState.Ready(Accelerator.GPU)
        assertTrue(ready is InitState.Ready)
        assertEquals(Accelerator.GPU, ready.accelerator)
    }

    @Test
    fun `snapshot is immutable`() {
        val original = RuntimeSnapshot(
            modelInfo = ModelInfo.Missing,
            initState = InitState.NotInitialized,
            lastInitMs = null,
            lastInferenceMs = null,
            lastFirstTokenMs = null,
            lastTokensEmitted = 0,
            lastError = null
        )
        val copy = original.copy(lastInferenceMs = 42L)
        assertNull(original.lastInferenceMs)
        assertEquals(42L, copy.lastInferenceMs)
    }

    @Test
    fun `eyewear is classified as an accessory`() {
        val result = ClothingVisionParser.parse("{\"name\":\"Glasses\",\"category\":\"GLASSES\"}")
        assertEquals(Category.ACCESSORY, result?.category)
    }

    @Test
    fun `jackets are classified as outerwear`() {
        val result = ClothingVisionParser.parse("{\"name\":\"Jacket\",\"category\":\"JACKET\"}")
        assertEquals(Category.OUTERWEAR, result?.category)
    }

    @Test
    fun `specific garment names override an incorrect top category`() {
        assertEquals(Category.BOTTOM, ClothingVisionParser.parse("{\"name\":\"Blue pants\",\"category\":\"TOP\"}")?.category)
        assertEquals(Category.ETHNIC_WEAR, ClothingVisionParser.parse("{\"name\":\"Red kurta\",\"category\":\"TOP\"}")?.category)
    }
}
