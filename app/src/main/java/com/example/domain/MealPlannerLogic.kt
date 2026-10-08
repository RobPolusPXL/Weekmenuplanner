package com.example.domain

import com.example.data.model.AggregatedGroceryItem
import com.example.data.model.DayKind
import com.example.data.model.DayPlan
import com.example.data.model.Dish
import com.example.data.model.IngredientItem
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.random.Random

/**
 * Pure domain logic for Maaltijdplanner:
 * - Belgian date formatting (dd/MM/yyyy) and week calculation (week starts on Sunday)
 * - 6.2 "Kies voor mij" local random + type rule algorithm
 * - 6.3 "Genereer hele week" local algorithm
 * - 6.5 Boodschappenlijst eenheidomrekening en samentellen
 */
object MealPlannerLogic {

    data class SimpleDate(val year: Int, val month: Int, val day: Int) : Comparable<SimpleDate> {
        fun toIsoString(): String = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
        fun toBelgianString(): String = String.format(Locale.ROOT, "%02d/%02d/%04d", day, month, year)
        fun toShortBelgianString(): String = String.format(Locale.ROOT, "%02d/%02d", day, month)

        override fun compareTo(other: SimpleDate): Int {
            if (year != other.year) return year.compareTo(other.year)
            if (month != other.month) return month.compareTo(other.month)
            return day.compareTo(other.day)
        }
    }

    private fun SimpleDate.toCalendar(): Calendar {
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun Calendar.toSimpleDate(): SimpleDate {
        return SimpleDate(
            year = get(Calendar.YEAR),
            month = get(Calendar.MONTH) + 1,
            day = get(Calendar.DAY_OF_MONTH)
        )
    }

    fun today(): SimpleDate {
        return Calendar.getInstance(TimeZone.getDefault()).toSimpleDate()
    }

    fun parseIsoDate(iso: String): SimpleDate? {
        val parts = iso.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return SimpleDate(y, m, d)
    }

    /**
     * Week starts on Sunday (Calendar.SUNDAY == 1).
     */
    fun getSundayOfWeek(date: SimpleDate = today()): SimpleDate {
        val cal = date.toCalendar()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday .. 7 = Saturday
        val daysSinceSunday = dayOfWeek - Calendar.SUNDAY
        if (daysSinceSunday != 0) {
            cal.add(Calendar.DAY_OF_MONTH, -daysSinceSunday)
        }
        return cal.toSimpleDate()
    }

    fun addDays(date: SimpleDate, days: Int): SimpleDate {
        val cal = date.toCalendar()
        cal.add(Calendar.DAY_OF_MONTH, days)
        return cal.toSimpleDate()
    }

    fun getWeekDates(sundayStart: SimpleDate): List<SimpleDate> {
        return (0..6).map { offset -> addDays(sundayStart, offset) }
    }

    fun getDutchDayName(date: SimpleDate): String {
        val cal = date.toCalendar()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "Zondag"
            Calendar.MONDAY -> "Maandag"
            Calendar.TUESDAY -> "Dinsdag"
            Calendar.WEDNESDAY -> "Woensdag"
            Calendar.THURSDAY -> "Donderdag"
            Calendar.FRIDAY -> "Vrijdag"
            Calendar.SATURDAY -> "Zaterdag"
            else -> ""
        }
    }

    fun getShortDutchDayName(date: SimpleDate): String {
        return getDutchDayName(date).take(2)
    }

    fun formatWeekHeader(sundayStart: SimpleDate): String {
        val saturdayEnd = addDays(sundayStart, 6)
        return "${sundayStart.toBelgianString()} – ${saturdayEnd.toBelgianString()}"
    }

    // --- 6.2 "Kies voor mij" (per dag) ---

    sealed interface PickResult {
        data object EmptyLibrary : PickResult
        data class Chosen(val dish: Dish, val ruleIgnored: Boolean) : PickResult
    }

    fun pickDishForDay(
        targetDateId: String,
        weekPlans: Map<String, DayPlan>,
        allDishes: List<Dish>,
        random: Random = Random.Default
    ): PickResult {
        if (allDishes.isEmpty()) return PickResult.EmptyLibrary

        val usedTypesOtherDays = weekPlans.values
            .filter { it.dateId != targetDateId && it.dayKind == DayKind.KOKEN }
            .mapNotNull { it.dishSnapshot?.type?.lowercase()?.trim() }
            .toSet()

        val currentDishIdOnDay = weekPlans[targetDateId]
            ?.takeIf { it.dayKind == DayKind.KOKEN }
            ?.dishId

        val strictCandidates = allDishes.filter { dish ->
            dish.id != currentDishIdOnDay && dish.type.lowercase().trim() !in usedTypesOtherDays
        }

        if (strictCandidates.isNotEmpty()) {
            val chosen = strictCandidates[random.nextInt(strictCandidates.size)]
            return PickResult.Chosen(dish = chosen, ruleIgnored = false)
        }

        // Fallback: Geen vrij type meer, kies willekeurig uit alle gerechten buiten het huidige gerecht
        val fallbackCandidates = allDishes.filter { it.id != currentDishIdOnDay }
            .ifEmpty { allDishes }
        val chosen = fallbackCandidates[random.nextInt(fallbackCandidates.size)]
        return PickResult.Chosen(dish = chosen, ruleIgnored = true)
    }

    // --- 6.3 "Genereer hele week" ---

    data class WeekGenerationResult(
        val assignments: Map<String, Dish>,
        val anyRuleIgnored: Boolean
    )

    fun generateWholeWeek(
        weekDateIds: List<String>,
        existingWeekPlans: Map<String, DayPlan>,
        allDishes: List<Dish>,
        random: Random = Random.Default
    ): WeekGenerationResult {
        if (allDishes.isEmpty()) return WeekGenerationResult(emptyMap(), false)

        val emptyDays = weekDateIds.filter { dateId ->
            val existing = existingWeekPlans[dateId]
            existing == null || existing.dayKind == DayKind.LEEG
        }.shuffled(random)

        val simulatedWeek = existingWeekPlans.toMutableMap()
        val assignments = mutableMapOf<String, Dish>()
        var anyRuleIgnored = false

        for (dateId in emptyDays) {
            when (val res = pickDishForDay(dateId, simulatedWeek, allDishes, random)) {
                is PickResult.Chosen -> {
                    assignments[dateId] = res.dish
                    if (res.ruleIgnored) anyRuleIgnored = true
                    simulatedWeek[dateId] = DayPlan(
                        dateId = dateId,
                        kind = DayKind.KOKEN.wireValue,
                        dishId = res.dish.id,
                        dishSnapshot = res.dish.toSnapshot()
                    )
                }
                PickResult.EmptyLibrary -> break
            }
        }

        return WeekGenerationResult(
            assignments = assignments,
            anyRuleIgnored = anyRuleIgnored
        )
    }

    // --- 6.5 Boodschappenlijst: eenheidomrekening en samentellen ---

    data class ConvertedUnitQuantity(
        val quantity: Double?,
        val canonicalUnit: String?
    )

    fun convertToCanonicalUnit(quantity: Double?, rawUnit: String?): ConvertedUnitQuantity {
        val unitClean = rawUnit?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (quantity == null) {
            val mappedUnit = when (unitClean) {
                "kilogram", "kg" -> "g"
                "gram", "gr", "g" -> "g"
                "liter", "l" -> "ml"
                "deciliter", "dl" -> "ml"
                "centiliter", "cl" -> "ml"
                "milliliter", "ml" -> "ml"
                "stuks", "stuk", "st" -> "stuk"
                "eetlepel", "eetlepels", "el" -> "el"
                "theelepel", "theelepels", "tl", "kl", "koffielepel" -> "tl"
                "teentje", "teentjes", "teen", "tenen" -> "teen"
                "blikken", "blik", "blikje" -> "blik"
                "pakken", "pak", "pakje" -> "pak"
                "bosje", "bosjes", "bos", "bussel" -> "bos"
                "plakken", "plakje", "plakjes", "plak" -> "plak"
                "sneden", "sneetje", "sneetjes", "snee" -> "snee"
                "potten", "potje", "potjes", "pot" -> "pot"
                "zakjes", "zak", "zakje" -> "zakje"
                else -> unitClean
            }
            return ConvertedUnitQuantity(null, mappedUnit)
        }

        return when (unitClean) {
            "kg", "kilogram" -> ConvertedUnitQuantity(quantity * 1000.0, "g")
            "g", "gr", "gram" -> ConvertedUnitQuantity(quantity, "g")
            "l", "liter" -> ConvertedUnitQuantity(quantity * 1000.0, "ml")
            "dl", "deciliter" -> ConvertedUnitQuantity(quantity * 100.0, "ml")
            "cl", "centiliter" -> ConvertedUnitQuantity(quantity * 10.0, "ml")
            "ml", "milliliter" -> ConvertedUnitQuantity(quantity, "ml")
            "stuks", "stuk", "st" -> ConvertedUnitQuantity(quantity, "stuk")
            "eetlepel", "eetlepels", "el" -> ConvertedUnitQuantity(quantity, "el")
            "theelepel", "theelepels", "tl", "kl", "koffielepel" -> ConvertedUnitQuantity(quantity, "tl")
            "teentje", "teentjes", "teen", "tenen" -> ConvertedUnitQuantity(quantity, "teen")
            "blikken", "blik", "blikje" -> ConvertedUnitQuantity(quantity, "blik")
            "pakken", "pak", "pakje" -> ConvertedUnitQuantity(quantity, "pak")
            "bosje", "bosjes", "bos", "bussel" -> ConvertedUnitQuantity(quantity, "bos")
            "plakken", "plakje", "plakjes", "plak" -> ConvertedUnitQuantity(quantity, "plak")
            "sneden", "sneetje", "sneetjes", "snee" -> ConvertedUnitQuantity(quantity, "snee")
            "potten", "potje", "potjes", "pot" -> ConvertedUnitQuantity(quantity, "pot")
            "zakjes", "zak", "zakje" -> ConvertedUnitQuantity(quantity, "zakje")
            else -> ConvertedUnitQuantity(quantity, unitClean)
        }
    }

    fun formatQuantityForDisplay(quantity: Double?, canonicalUnit: String?): String {
        if (quantity == null) return ""
        val unit = canonicalUnit?.trim()?.lowercase()
        val displayQty: Double
        val displayUnit: String?

        when {
            unit == "g" && quantity >= 1000.0 -> {
                displayQty = quantity / 1000.0
                displayUnit = "kg"
            }
            unit == "ml" && quantity >= 1000.0 -> {
                displayQty = quantity / 1000.0
                displayUnit = "l"
            }
            else -> {
                displayQty = quantity
                displayUnit = unit
            }
        }

        val numStr = formatNumberBelgian(displayQty)
        return if (displayUnit.isNullOrBlank()) {
            numStr
        } else {
            "$numStr $displayUnit"
        }
    }

    fun formatNumberBelgian(value: Double): String {
        val roundedInt = value.toLong()
        if (abs(value - roundedInt.toDouble()) < 0.001) {
            return roundedInt.toString()
        }
        val symbols = DecimalFormatSymbols(Locale("nl", "BE")).apply {
            decimalSeparator = ','
        }
        return DecimalFormat("0.##", symbols).format(value)
    }

    fun aggregateGroceryList(
        weekDays: Collection<DayPlan>,
        checkedMap: Map<String, Boolean>
    ): List<AggregatedGroceryItem> {
        data class GroupAccumulator(
            val name: String,
            val canonicalUnit: String?,
            var sumQuantity: Double = 0.0,
            var hasAnyQuantity: Boolean = false,
            val dishNames: MutableSet<String> = linkedSetOf()
        )

        val groups = mutableMapOf<String, GroupAccumulator>()

        for (day in weekDays) {
            if (day.dayKind != DayKind.KOKEN) continue
            val snapshot = day.dishSnapshot ?: continue
            for (ing in snapshot.ingredients) {
                val cleanName = ing.name.trim().lowercase().ifEmpty { ing.raw.trim().lowercase() }
                if (cleanName.isEmpty()) continue

                val converted = convertToCanonicalUnit(ing.quantity, ing.unit)
                val unitPart = converted.canonicalUnit ?: ""
                val itemKey = "$cleanName|$unitPart"

                val acc = groups.getOrPut(itemKey) {
                    GroupAccumulator(
                        name = cleanName,
                        canonicalUnit = converted.canonicalUnit
                    )
                }
                if (converted.quantity != null) {
                    acc.sumQuantity += converted.quantity
                    acc.hasAnyQuantity = true
                }
                if (snapshot.name.isNotBlank()) {
                    acc.dishNames.add(snapshot.name)
                }
            }
        }

        val items = groups.map { (itemKey, acc) ->
            val finalQty = if (acc.hasAnyQuantity) acc.sumQuantity else null
            val displayAmount = formatQuantityForDisplay(finalQty, acc.canonicalUnit)
            val isChecked = checkedMap[itemKey] == true
            AggregatedGroceryItem(
                itemKey = itemKey,
                name = acc.name,
                totalQuantity = finalQty,
                canonicalUnit = acc.canonicalUnit,
                displayAmount = displayAmount,
                checked = isChecked,
                dishNames = acc.dishNames.toList()
            )
        }

        // Sortering alfabetisch, afgevinkte items zakken onderaan
        return items.sortedWith(
            compareBy<AggregatedGroceryItem> { it.checked }
                .thenBy { it.name.lowercase() }
                .thenBy { it.canonicalUnit ?: "" }
        )
    }

    /**
     * Fallback local parser if user enters raw ingredient line and wants instant preview,
     * while offline/failed Gemini strictly follows section 6.6:
     * "Offline of bij falen: zet name = lowercase getrimde raw, quantity en unit = null en needsNormalization = true."
     */
    fun createOfflineFallbackIngredient(rawLine: String): IngredientItem {
        val trimmed = rawLine.trim()
        return IngredientItem(
            raw = trimmed,
            name = trimmed.lowercase(),
            quantity = null,
            unit = null,
            needsNormalization = true
        )
    }
}
