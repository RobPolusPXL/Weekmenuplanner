package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DayKind
import com.example.data.model.DayPlan
import com.example.data.model.Dish
import com.example.data.model.IngredientItem
import com.example.domain.MealPlannerLogic
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MaaltijdRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun extractFirestoreException(t: Throwable): FirebaseFirestoreException? {
        return generateSequence(t) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    @Test
    fun createHousehold_andJoinWithSecondMember_andManageDishesAndDays() = runBlocking {
        val robUid = signInTestUser("rob@test.be")
        val repo = MaaltijdRepository(firestore, auth)

        val code = MaaltijdRepository.generateInviteCode()
        val createRes = withTimeout(5000L) { repo.createHousehold(code) }
        assertTrue("Household creation should succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)
        val household = createRes.getOrThrow()
        assertEquals(code, household.inviteCode)

        // Joke logs in and joins using the 6-char inviteCode
        val jokeUid = signInTestUser("joke@test.be")
        val joinRes = withTimeout(5000L) { repo.joinHouseholdByInviteCode(code) }
        assertTrue("Joke joining household should succeed: ${joinRes.exceptionOrNull()}", joinRes.isSuccess)

        // Observe household as Joke
        val observedHousehold = withTimeout(3000L) {
            repo.observeUserHousehold(jokeUid).first { it != null && it.id == code }
        }
        assertNotNull(observedHousehold)
        assertEquals(listOf(robUid, jokeUid), observedHousehold!!.members)

        // Joke creates a dish
        val saveDishRes = withTimeout(5000L) {
            repo.saveDish(
                hid = code,
                existingDishId = null,
                name = "Stoofvlees met frietjes",
                type = "vlees",
                ingredients = listOf(
                    IngredientItem(raw = "2 uien", name = "ui", quantity = 2.0, unit = "stuk", needsNormalization = false),
                    IngredientItem(raw = "800 g stoofvlees", name = "stoofvlees", quantity = 800.0, unit = "g", needsNormalization = false)
                ),
                recipeUrl = "https://dagelijksekost.vrt.be",
                note = "Met bruin bier",
                photoUrl = null
            )
        }
        assertTrue("Dish creation should succeed: ${saveDishRes.exceptionOrNull()}", saveDishRes.isSuccess)
        val savedDish = saveDishRes.getOrThrow()

        // Rob logs back in and plans the dish on Sunday
        signInTestUser("rob@test.be")
        val planRes = withTimeout(5000L) {
            repo.setDayCooking(code, "2026-10-11", savedDish)
        }
        assertTrue("Planning day should succeed: ${planRes.exceptionOrNull()}", planRes.isSuccess)

        // Rob checks off an item on the week grocery list
        val checkRes = withTimeout(5000L) {
            repo.setGroceryItemChecked(code, "2026-10-11", "ui|stuk", true, emptyMap())
        }
        assertTrue("Checking grocery item should succeed: ${checkRes.exceptionOrNull()}", checkRes.isSuccess)
    }

    @Test
    fun nonMember_cannotReadOrWriteHouseholdDishes() = runBlocking {
        signInTestUser("rob2@test.be")
        val repo = MaaltijdRepository(firestore, auth)
        val code = MaaltijdRepository.generateInviteCode()
        withTimeout(5000L) { repo.createHousehold(code).getOrThrow() }

        // Charlie (non-member) tries to read dishes of Rob's household
        signInTestUser("charlie@test.be")
        try {
            withTimeout(3000L) {
                repo.observeDishes(code).first()
            }
            fail("Expected PERMISSION_DENIED for non-member reading household dishes")
        } catch (t: Throwable) {
            val fsEx = extractFirestoreException(t)
            assertNotNull("Expected FirebaseFirestoreException in $t", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }

        // Charlie also cannot write a dish to Rob's household
        val writeRes = withTimeout(5000L) {
            repo.saveDish(
                hid = code,
                existingDishId = null,
                name = " verboden gerecht",
                type = "pasta",
                ingredients = emptyList(),
                recipeUrl = null,
                note = null,
                photoUrl = null
            )
        }
        assertTrue(writeRes.isFailure)
        val writeFsEx = extractFirestoreException(writeRes.exceptionOrNull()!!)
        assertNotNull(writeFsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, writeFsEx!!.code)
    }

    @Test
    fun unauthenticatedUser_cannotQueryHouseholds() = runBlocking {
        auth.signOut()
        val repo = MaaltijdRepository(firestore, auth)
        try {
            withTimeout(3000L) {
                repo.observeUserHousehold("any_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated user")
        } catch (t: Throwable) {
            val fsEx = extractFirestoreException(t)
            assertNotNull("Expected FirebaseFirestoreException in $t", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    @Test
    fun domainLogic_pickDishForDay_and_generateWholeWeek_enforcesTypeRule() {
        val dishes = listOf(
            Dish(id = "d1", name = "Spaghetti", type = "pasta"),
            Dish(id = "d2", name = "Nasi Goreng", type = "rijst"),
            Dish(id = "d3", name = "hutsepot", type = "aardappelen")
        )
        val existingPlans = mapOf(
            "2026-10-11" to DayPlan(
                dateId = "2026-10-11",
                kind = DayKind.KOKEN.wireValue,
                dishId = "d1",
                dishSnapshot = dishes[0].toSnapshot()
            )
        )

        // Pick for Monday should never choose "pasta" since Sunday already has "pasta"
        val pickRes = MealPlannerLogic.pickDishForDay("2026-10-12", existingPlans, dishes)
        assertTrue(pickRes is MealPlannerLogic.PickResult.Chosen)
        val chosen = pickRes as MealPlannerLogic.PickResult.Chosen
        assertFalse(chosen.ruleIgnored)
        assertTrue(chosen.dish.type in setOf("rijst", "aardappelen"))
    }

    @Test
    fun domainLogic_aggregateGroceryList_convertsUnitsAndSumsCorrectly() {
        val dish1 = Dish(
            id = "d1",
            name = "Stoofvlees",
            type = "vlees",
            ingredients = listOf(
                IngredientItem(raw = "2 uien", name = "ui", quantity = 2.0, unit = "stuk"),
                IngredientItem(raw = "200 g ui", name = "ui", quantity = 200.0, unit = "g"),
                IngredientItem(raw = "0.6 kg aardappel", name = "aardappel", quantity = 0.6, unit = "kg"),
                IngredientItem(raw = "peper en zout", name = "peper en zout", quantity = null, unit = null)
            )
        )
        val dish2 = Dish(
            id = "d2",
            name = "Ovenschotel",
            type = "ovenschotel",
            ingredients = listOf(
                IngredientItem(raw = "3 uien", name = "ui", quantity = 3.0, unit = "stuk"),
                IngredientItem(raw = "500 g aardappel", name = "aardappel", quantity = 500.0, unit = "g"),
                IngredientItem(raw = "8 dl melk", name = "melk", quantity = 8.0, unit = "dl"),
                IngredientItem(raw = "400 ml melk", name = "melk", quantity = 400.0, unit = "ml")
            )
        )

        val days = listOf(
            DayPlan(dateId = "2026-10-11", kind = "koken", dishId = "d1", dishSnapshot = dish1.toSnapshot()),
            DayPlan(dateId = "2026-10-12", kind = "koken", dishId = "d2", dishSnapshot = dish2.toSnapshot()),
            DayPlan(dateId = "2026-10-13", kind = "afhaal", note = "Frituur")
        )

        val aggregated = MealPlannerLogic.aggregateGroceryList(days, mapOf("ui|stuk" to true))

        // Different units of "ui" ("stuk" and "g") remain separate items
        val uiStuk = aggregated.first { it.itemKey == "ui|stuk" }
        val uiGram = aggregated.first { it.itemKey == "ui|g" }
        assertEquals(5.0, uiStuk.totalQuantity!!, 0.001)
        assertEquals("5 stuk", uiStuk.displayAmount)
        assertTrue(uiStuk.checked)

        assertEquals(200.0, uiGram.totalQuantity!!, 0.001)
        assertEquals("200 g", uiGram.displayAmount)
        assertFalse(uiGram.checked)

        // 0.6 kg + 500 g = 1100 g -> displayed as 1,1 kg
        val aardappel = aggregated.first { it.itemKey == "aardappel|g" }
        assertEquals(1100.0, aardappel.totalQuantity!!, 0.001)
        assertEquals("1,1 kg", aardappel.displayAmount)

        // 8 dl (800 ml) + 400 ml = 1200 ml -> displayed as 1,2 l
        val melk = aggregated.first { it.itemKey == "melk|ml" }
        assertEquals(1200.0, melk.totalQuantity!!, 0.001)
        assertEquals("1,2 l", melk.displayAmount)

        // Ingredient without quantity shows name without quantity
        val peper = aggregated.first { it.itemKey == "peper en zout|" }
        assertNull(peper.totalQuantity)
        assertEquals("", peper.displayAmount)

        // Checked item ("ui|stuk") sinks to the bottom of the sorted list
        assertEquals("ui|stuk", aggregated.last().itemKey)
    }
}
