package com.example.domain

import com.example.data.model.DayKind
import com.example.data.model.DayPlan
import com.example.data.model.DishSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsLogicTest {

    private fun cook(date: String, dishId: String, name: String, type: String) = DayPlan(
        dateId = date,
        kind = DayKind.KOKEN.wireValue,
        dishId = dishId,
        dishSnapshot = DishSnapshot(name = name, type = type)
    )

    private fun marker(date: String, kind: DayKind) = DayPlan(dateId = date, kind = kind.wireValue)

    private val days = listOf(
        cook("2026-10-01", "a", "Lasagne", "pasta"),
        cook("2026-10-03", "a", "Lasagne", "pasta"),
        cook("2026-10-04", "b", "Stoofvlees", "vlees"),
        marker("2026-10-02", DayKind.AFHAAL),
        marker("2026-10-05", DayKind.OPWARM),
        marker("2026-10-06", DayKind.NIET_KOKEN),
        marker("2026-10-07", DayKind.LEEG),
        cook("2026-09-30", "c", "Soep", "soep"),
        cook("2025-12-24", "a", "Lasagne", "pasta"),
        cook("2026-10-20", "b", "Stoofvlees", "vlees") // in de toekomst: telt niet mee
    )

    @Test
    fun `maandstatistiek telt per soort en negeert andere maanden en de toekomst`() {
        val s = StatsLogic.compute(days, "2026-10", todayIso = "2026-10-09")
        assertEquals(3, s.cooked)
        assertEquals(1, s.takeaway)
        assertEquals(1, s.reheated)
        assertEquals(1, s.noCook)
        assertEquals(6, s.total)
        assertEquals(listOf("Lasagne" to 2, "Stoofvlees" to 1), s.topDishes.map { it.label to it.count })
        assertEquals(listOf("pasta" to 2, "vlees" to 1), s.types.map { it.label to it.count })
    }

    @Test
    fun `jaarstatistiek telt alle maanden van dat jaar`() {
        val s = StatsLogic.compute(days, "2026", todayIso = "2026-10-09")
        assertEquals(4, s.cooked) // 3 in oktober + soep in september
        assertEquals("Lasagne", s.topDishes.first().label)
    }

    @Test
    fun `lege periode geeft nullen`() {
        val s = StatsLogic.compute(days, "2024", todayIso = "2026-10-09")
        assertEquals(0, s.total)
        assertEquals(emptyList<StatsLogic.CountedItem>(), s.topDishes)
    }
}
