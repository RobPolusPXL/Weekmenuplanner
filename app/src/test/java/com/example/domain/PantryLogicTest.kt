package com.example.domain

import com.example.data.model.AggregatedGroceryItem
import com.example.data.model.PantryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryLogicTest {

    private val w1 = "2026-10-25"
    private val w2 = "2026-11-01"

    @Test
    fun `voorraad dekt eerst de vroegste week`() {
        val r = PantryLogic.reconcile(
            freeStock = mapOf("rijst|g" to 500.0),
            currentReservations = emptyMap(),
            needsPerWeek = mapOf(w1 to mapOf("rijst|g" to 300.0), w2 to mapOf("rijst|g" to 300.0)),
            activeWeeks = listOf(w2, w1)
        )
        assertEquals(300.0, r.reservations[w1]!!["rijst|g"]!!, 0.001)
        assertEquals(200.0, r.reservations[w2]!!["rijst|g"]!!, 0.001)
        assertEquals(0.0, r.freeStock["rijst|g"]!!, 0.001)
    }

    @Test
    fun `opnieuw berekenen verandert niets meer`() {
        val first = PantryLogic.reconcile(
            mapOf("rijst|g" to 500.0), emptyMap(),
            mapOf(w1 to mapOf("rijst|g" to 300.0)), listOf(w1)
        )
        val second = PantryLogic.reconcile(first.freeStock, first.reservations, mapOf(w1 to mapOf("rijst|g" to 300.0)), listOf(w1))
        assertFalse(PantryLogic.hasChanges(first.freeStock, first.reservations, listOf(w1), second))
    }

    @Test
    fun `gerecht schrappen geeft de voorraad terug`() {
        val r = PantryLogic.reconcile(
            freeStock = mapOf("rijst|g" to 200.0),
            currentReservations = mapOf(w1 to mapOf("rijst|g" to 300.0)),
            needsPerWeek = emptyMap(),
            activeWeeks = listOf(w1)
        )
        assertEquals(500.0, r.freeStock["rijst|g"]!!, 0.001)
        assertTrue(r.reservations[w1]!!.isEmpty())
    }

    @Test
    fun `reservering voor verwijderd voorraaditem vervalt`() {
        val current = mapOf(w1 to mapOf("pasta|g" to 250.0))
        val r = PantryLogic.reconcile(emptyMap(), current, mapOf(w1 to mapOf("pasta|g" to 250.0)), listOf(w1))
        assertTrue(r.reservations[w1]!!.isEmpty())
        assertTrue(PantryLogic.hasChanges(emptyMap(), current, listOf(w1), r))
    }

    private fun item(name: String, qty: Double?, unit: String?) = AggregatedGroceryItem(
        itemKey = "$name|${unit ?: ""}", name = name, totalQuantity = qty, canonicalUnit = unit,
        displayAmount = "", checked = false, dishNames = emptyList()
    )

    @Test
    fun `lijst wordt gesplitst in kopen en uit voorraad`() {
        val items = listOf(item("rijst", 300.0, "g"), item("gehakt", 500.0, "g"), item("ui", 3.0, "stuk"), item("zout", null, null))
        val pantry = listOf(
            PantryItem(id = "1", name = "rijst", unit = "g", quantity = 0.0),
            PantryItem(id = "2", name = "ui", unit = "stuk", quantity = 0.0),
            PantryItem(id = "3", name = "zout", unit = null, quantity = null)
        )
        val split = PantryLogic.splitGroceryList(items, mapOf("rijst|g" to 300.0, "ui|stuk" to 1.0), pantry)
        assertEquals(listOf("gehakt", "ui"), split.toBuy.map { it.name })
        assertEquals(2.0, split.toBuy.first { it.name == "ui" }.totalQuantity!!, 0.001)
        assertEquals(listOf("rijst", "zout"), split.fromStock.map { it.name })
    }

    @Test
    fun `invoer wordt geparsed`() {
        val p = PantryLogic.parseInput("1 kg rijst")!!
        assertEquals("rijst", p.name)
        assertEquals("g", p.unit)
        assertEquals(1000.0, p.quantity!!, 0.001)
        val basis = PantryLogic.parseInput("zout")!!
        assertEquals(null, basis.quantity)
        assertEquals(1.5, PantryLogic.parseAmount("1,5")!!, 0.001)
    }

    @Test
    fun `bijna op`() {
        assertTrue(PantryItem(name = "rijst", unit = "g", quantity = 150.0, minQuantity = 200.0).isLow)
        assertFalse(PantryItem(name = "rijst", unit = "g", quantity = 500.0, minQuantity = 200.0).isLow)
        assertTrue(PantryItem(name = "rijst", unit = "g", quantity = 0.0).isLow)
        assertFalse(PantryItem(name = "zout").isLow)
    }
}
