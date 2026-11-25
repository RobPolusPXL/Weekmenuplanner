package com.example.domain

import com.example.domain.MealPlannerLogic.SimpleDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class WeekStartTest {
    // Zaterdag 31 oktober 2026
    private val saturday = SimpleDate(2026, 10, 31)

    @Test
    fun `week op zondag begint op de zondag ervoor`() {
        assertEquals(SimpleDate(2026, 10, 25), MealPlannerLogic.getWeekStart(saturday, Calendar.SUNDAY))
        assertEquals(SimpleDate(2026, 10, 25), MealPlannerLogic.getSundayOfWeek(saturday))
    }

    @Test
    fun `week op maandag begint op de maandag ervoor`() {
        assertEquals(SimpleDate(2026, 10, 26), MealPlannerLogic.getWeekStart(saturday, Calendar.MONDAY))
    }

    @Test
    fun `startdag zelf blijft staan`() {
        assertEquals(saturday, MealPlannerLogic.getWeekStart(saturday, Calendar.SATURDAY))
    }

    @Test
    fun `week op vrijdag begint de dag ervoor`() {
        assertEquals(SimpleDate(2026, 10, 30), MealPlannerLogic.getWeekStart(saturday, Calendar.FRIDAY))
    }

    @Test
    fun `week van zeven dagen vanaf de startdag`() {
        val dates = MealPlannerLogic.getWeekDates(SimpleDate(2026, 10, 26))
        assertEquals(7, dates.size)
        assertEquals(SimpleDate(2026, 11, 1), dates.last())
        assertEquals("26/10/2026 – 01/11/2026", MealPlannerLogic.formatWeekHeader(SimpleDate(2026, 10, 26)))
    }

    @Test
    fun `dagen per week groeperen volgt de startdag`() {
        val days = mapOf(
            "2026-10-25" to com.example.data.model.DayPlan(dateId = "2026-10-25"),
            "2026-10-26" to com.example.data.model.DayPlan(dateId = "2026-10-26")
        )
        // zondag 25/10 hoort bij de maandagweek van 19/10, maandag 26/10 bij die van 26/10
        assertEquals(setOf("2026-10-19", "2026-10-26"), PantryLogic.daysByWeek(days, Calendar.MONDAY).keys)
        assertEquals(setOf("2026-10-25"), PantryLogic.daysByWeek(days, Calendar.SUNDAY).keys)
    }
}
