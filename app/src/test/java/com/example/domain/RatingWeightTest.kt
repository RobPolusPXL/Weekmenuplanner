package com.example.domain

import com.example.data.model.Dish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RatingWeightTest {

    @Test
    fun `meer sterren geeft meer gewicht en niet beoordeeld telt als drie sterren`() {
        assertEquals(1, MealPlannerLogic.ratingWeight(Dish(rating = 1)))
        assertEquals(2, MealPlannerLogic.ratingWeight(Dish(rating = 2)))
        assertEquals(3, MealPlannerLogic.ratingWeight(Dish(rating = 3)))
        assertEquals(5, MealPlannerLogic.ratingWeight(Dish(rating = 4)))
        assertEquals(8, MealPlannerLogic.ratingWeight(Dish(rating = 5)))
        assertEquals(3, MealPlannerLogic.ratingWeight(Dish(rating = 0)))
    }

    @Test
    fun `vijfsterrengerecht wordt veel vaker gekozen dan eensterrengerecht`() {
        val fav = Dish(id = "fav", name = "Favoriet", type = "pasta", rating = 5)
        val meh = Dish(id = "meh", name = "Meh", type = "rijst", rating = 1)
        val random = Random(42)
        val counts = mutableMapOf("fav" to 0, "meh" to 0)
        repeat(4000) {
            val picked = MealPlannerLogic.pickWeighted(listOf(fav, meh), random)
            counts[picked.id] = counts.getValue(picked.id) + 1
        }
        // Verwacht verhouding 8:1; ruime marge.
        assertTrue("fav=${counts["fav"]} meh=${counts["meh"]}", counts.getValue("fav") > counts.getValue("meh") * 4)
        assertTrue(counts.getValue("meh") > 0)
    }
}
