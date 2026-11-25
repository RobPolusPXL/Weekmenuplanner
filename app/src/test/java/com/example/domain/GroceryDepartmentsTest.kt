package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GroceryDepartmentsTest {
    private fun d(n: String) = GroceryDepartments.departmentFor(n)

    @Test fun groenten() {
        assertEquals(GroceryDepartment.GROENTEN_FRUIT, d("ui"))
        assertEquals(GroceryDepartment.GROENTEN_FRUIT, d("Rode paprika"))
        assertEquals(GroceryDepartment.GROENTEN_FRUIT, d("tomaten"))
    }

    @Test fun vleesEnZuivel() {
        assertEquals(GroceryDepartment.VLEES_VIS, d("gehakt"))
        assertEquals(GroceryDepartment.VLEES_VIS, d("kipfilet"))
        assertEquals(GroceryDepartment.ZUIVEL, d("eieren"))
        assertEquals(GroceryDepartment.ZUIVEL, d("geraspte kaas"))
    }

    @Test fun specifiekeRegelsWinnen() {
        assertEquals(GroceryDepartment.KRUIDEN_SAUZEN, d("kippenbouillon"))
        assertEquals(GroceryDepartment.KRUIDEN_SAUZEN, d("kokosmelk"))
        assertEquals(GroceryDepartment.KRUIDEN_SAUZEN, d("pindakaas"))
        assertEquals(GroceryDepartment.KRUIDEN_SAUZEN, d("tomatenpuree"))
        assertEquals(GroceryDepartment.KRUIDEN_SAUZEN, d("paprikapoeder"))
    }

    @Test fun uitzonderingen() {
        assertEquals(GroceryDepartment.ZUIVEL, d("zoute boter"))
        assertEquals(GroceryDepartment.GROENTEN_FRUIT, d("watermeloen"))
        assertEquals(GroceryDepartment.GROENTEN_FRUIT, d("zoete aardappel"))
    }

    @Test fun drogeWarenEnOverig() {
        assertEquals(GroceryDepartment.DROGE_WAREN, d("spaghetti"))
        assertEquals(GroceryDepartment.DROGE_WAREN, d("rijst"))
        assertEquals(GroceryDepartment.OVERIG, d("keukenrol"))
        assertEquals(GroceryDepartment.OVERIG, d(""))
    }

    @Test fun groepeertInWinkelvolgorde() {
        val g = GroceryDepartments.group(listOf("rijst", "gehakt", "ui", "kaas")) { it }
        assertEquals(
            listOf(GroceryDepartment.GROENTEN_FRUIT, GroceryDepartment.VLEES_VIS, GroceryDepartment.ZUIVEL, GroceryDepartment.DROGE_WAREN),
            g.map { it.first }
        )
    }
}
