package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientParserTest {

    @Test
    fun `150 gram gehakt wordt gehakt 150 g`() {
        val item = IngredientParser.parse("150 gram gehakt")
        assertEquals("gehakt", item.name)
        assertEquals(150.0, item.quantity!!, 0.001)
        assertEquals("g", item.unit)
        assertFalse(item.needsNormalization)
    }

    @Test
    fun `eenheid direct achter het getal`() {
        val item = IngredientParser.parse("150g gehakt")
        assertEquals("gehakt", item.name)
        assertEquals(150.0, item.quantity!!, 0.001)
        assertEquals("g", item.unit)
    }

    @Test
    fun `voorbeeldlijst uit de specificatie`() {
        val expected = listOf(
            Triple("2 uien", "ui", Pair(2.0, "stuk")),
            Triple("500 g gehakt", "gehakt", Pair(500.0, "g")),
            Triple("1,5 kg patatten", "aardappel", Pair(1500.0, "g")),
            Triple("1/2 bloemkool", "bloemkool", Pair(0.5, "stuk")),
            Triple("2 el olijfolie", "olijfolie", Pair(2.0, "el")),
            Triple("3 teentjes look", "knoflook", Pair(3.0, "teen")),
            Triple("1 blik tomatenblokjes", "tomatenblokjes", Pair(1.0, "blik")),
            Triple("250 ml room", "room", Pair(250.0, "ml"))
        )
        for ((raw, name, qtyUnit) in expected) {
            val item = IngredientParser.parse(raw)
            assertEquals(raw, name, item.name)
            assertEquals(raw, qtyUnit.first, item.quantity!!, 0.001)
            assertEquals(raw, qtyUnit.second, item.unit)
        }
    }

    @Test
    fun `zonder hoeveelheid`() {
        val snuf = IngredientParser.parse("snuf zout")
        assertEquals("zout", snuf.name)
        assertNull(snuf.quantity)
        assertNull(snuf.unit)

        val peper = IngredientParser.parse("peper")
        assertEquals("peper", peper.name)
        assertNull(peper.quantity)
        assertNull(peper.unit)
    }

    @Test
    fun `meervoud en enkelvoud komen op dezelfde naam uit`() {
        assertEquals("ui", IngredientParser.parse("2 uien").name)
        assertEquals("ui", IngredientParser.parse("1 ui").name)
        assertEquals("rode ui", IngredientParser.parse("2 rode uien").name)
    }

    @Test
    fun `kilo en liter worden omgerekend`() {
        val kilo = IngredientParser.parse("1 kilo aardappelen")
        assertEquals("aardappel", kilo.name)
        assertEquals(1000.0, kilo.quantity!!, 0.001)
        assertEquals("g", kilo.unit)

        val liter = IngredientParser.parse("0,5 l melk")
        assertEquals("melk", liter.name)
        assertEquals(500.0, liter.quantity!!, 0.001)
        assertEquals("ml", liter.unit)
    }

    @Test
    fun `alleen getal en eenheid blijft als getypt`() {
        val item = IngredientParser.parse("150 gram")
        assertEquals("150 gram", item.name)
        assertNull(item.quantity)
    }
}
