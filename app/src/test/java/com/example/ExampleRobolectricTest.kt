package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.IngredientNormalizerService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Maaltijdplanner", appName)
    }

    @Test
    fun `parseNormalizedJson parses Deel B example and rejects mismatched length`() {
        val rawInput = listOf(
            "2 uien",
            "500 g gehakt",
            "1,5 kg patatten",
            "1/2 bloemkool",
            "snuf zout",
            "2 el olijfolie",
            "3 teentjes look",
            "1 blik tomatenblokjes",
            "250 ml room",
            "peper"
        )
        val geminiJson = """[{"name":"ui","quantity":2,"unit":"stuk"},{"name":"gehakt","quantity":500,"unit":"g"},{"name":"aardappel","quantity":1500,"unit":"g"},{"name":"bloemkool","quantity":0.5,"unit":"stuk"},{"name":"zout","quantity":null,"unit":null},{"name":"olijfolie","quantity":2,"unit":"el"},{"name":"knoflook","quantity":3,"unit":"teen"},{"name":"tomatenblokjes","quantity":1,"unit":"blik"},{"name":"room","quantity":250,"unit":"ml"},{"name":"peper","quantity":null,"unit":null}]"""

        val parsed = IngredientNormalizerService.parseNormalizedJson(geminiJson, rawInput)
        assertNotNull(parsed)
        assertEquals(10, parsed!!.size)
        assertEquals("ui", parsed[0].name)
        assertEquals(2.0, parsed[0].quantity!!, 0.001)
        assertEquals("stuk", parsed[0].unit)
        assertFalse(parsed[0].needsNormalization)

        assertEquals("aardappel", parsed[2].name)
        assertEquals(1500.0, parsed[2].quantity!!, 0.001)
        assertEquals("g", parsed[2].unit)

        assertEquals("zout", parsed[4].name)
        assertNull(parsed[4].quantity)
        assertNull(parsed[4].unit)

        // Mismatched element count must return null so fallback 6.6 is used
        val wrongSizeJson = """[{"name":"ui","quantity":2,"unit":"stuk"}]"""
        assertNull(IngredientNormalizerService.parseNormalizedJson(wrongSizeJson, rawInput))
    }

    @Test
    fun `offline normalization uses fallback 6_6 with needsNormalization true`() = runBlocking {
        val rawInput = listOf("2 uien", "500 g gehakt")
        val fallback = IngredientNormalizerService.normalizeIngredients(rawInput, isOnline = false)
        assertEquals(2, fallback.size)
        assertEquals("2 uien", fallback[0].name)
        assertNull(fallback[0].quantity)
        assertNull(fallback[0].unit)
        assertTrue(fallback[0].needsNormalization)
    }
}
