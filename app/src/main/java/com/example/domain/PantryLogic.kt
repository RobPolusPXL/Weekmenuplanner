package com.example.domain

import com.example.data.model.AggregatedGroceryItem
import com.example.data.model.DayPlan
import com.example.data.model.PantryItem
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Voorraadlogica (puur Kotlin, geen Firebase).
 *
 * Model: een voorraaditem bevat de VRIJE hoeveelheid. Wat geplande maaltijden van de huidige
 * en komende weken nodig hebben, wordt gereserveerd (per week) en van de vrije voorraad afgetrokken.
 * Wordt een maaltijd geschrapt, dan komt de reservering terug in de voorraad.
 * Zodra een week voorbij is, blijft haar reservering staan: die is dan opgegeten.
 */
object PantryLogic {
    private const val EPS = 0.0001

    fun round(v: Double): Double = (v * 1000.0).roundToLong() / 1000.0

    /** Benodigde hoeveelheden per itemKey voor één week (alleen regels met een hoeveelheid). */
    fun weekNeeds(weekDays: Collection<DayPlan>): Map<String, Double> =
        MealPlannerLogic.aggregateGroceryList(weekDays, emptyMap())
            .mapNotNull { item -> item.totalQuantity?.let { item.itemKey to it } }
            .toMap()

    /** Groepeert dagen per weekstart (zondag, ISO-datum). */
    fun daysByWeek(allDays: Map<String, DayPlan>): Map<String, List<DayPlan>> =
        allDays.values
            .groupBy { day ->
                MealPlannerLogic.parseIsoDate(day.dateId)
                    ?.let { MealPlannerLogic.getSundayOfWeek(it).toIsoString() } ?: ""
            }
            .filterKeys { it.isNotEmpty() }

    /** Huidige en toekomstige weken die geplande dagen of reserveringen hebben. */
    fun activeWeeks(
        currentWeekStart: String,
        plannedWeeks: Collection<String>,
        reservedWeeks: Collection<String>
    ): List<String> = (plannedWeeks + reservedWeeks)
        .filter { it >= currentWeekStart }
        .distinct()
        .sorted()

    data class ReconcileResult(
        /** itemKey -> nieuwe vrije hoeveelheid (enkel getelde voorraaditems). */
        val freeStock: Map<String, Double>,
        /** weekStart -> itemKey -> gereserveerde hoeveelheid. */
        val reservations: Map<String, Map<String, Double>>
    )

    /**
     * Verdeelt de voorraad over de actieve weken, vroegste week eerst.
     * pool = vrije voorraad + wat nu al in actieve weken gereserveerd staat.
     * Reserveringen voor items die niet (meer) in de voorraad staan vervallen.
     */
    fun reconcile(
        freeStock: Map<String, Double>,
        currentReservations: Map<String, Map<String, Double>>,
        needsPerWeek: Map<String, Map<String, Double>>,
        activeWeeks: List<String>
    ): ReconcileResult {
        val weeks = activeWeeks.sorted()
        val newFree = mutableMapOf<String, Double>()
        val newRes = weeks.associateWith { mutableMapOf<String, Double>() }
        for ((key, free) in freeStock) {
            var pool = free + weeks.sumOf { currentReservations[it]?.get(key) ?: 0.0 }
            for (w in weeks) {
                val need = needsPerWeek[w]?.get(key) ?: 0.0
                val take = round(minOf(need, pool))
                if (take > EPS) {
                    newRes.getValue(w)[key] = take
                    pool -= take
                }
            }
            newFree[key] = round(maxOf(0.0, pool))
        }
        return ReconcileResult(newFree, newRes)
    }

    fun hasChanges(
        freeStock: Map<String, Double>,
        currentReservations: Map<String, Map<String, Double>>,
        activeWeeks: List<String>,
        result: ReconcileResult
    ): Boolean {
        if (result.freeStock.any { (k, v) -> abs((freeStock[k] ?: 0.0) - v) > EPS }) return true
        for (w in activeWeeks) {
            val a = currentReservations[w].orEmpty().filterValues { it > EPS }
            val b = result.reservations[w].orEmpty()
            if (a.keys != b.keys) return true
            if (a.any { (k, v) -> abs(v - (b[k] ?: 0.0)) > EPS }) return true
        }
        return false
    }

    data class StockSplit(
        val toBuy: List<AggregatedGroceryItem>,
        val fromStock: List<AggregatedGroceryItem>
    )

    /**
     * Splitst de boodschappenlijst van een week in "nog kopen" en "uit voorraad".
     * reserved = reservering van die week (itemKey -> hoeveelheid).
     */
    fun splitGroceryList(
        items: List<AggregatedGroceryItem>,
        reserved: Map<String, Double>,
        pantry: List<PantryItem>
    ): StockSplit {
        val basisNames = pantry.filter { !it.isTracked }.map { it.name }.toSet()
        val toBuy = mutableListOf<AggregatedGroceryItem>()
        val fromStock = mutableListOf<AggregatedGroceryItem>()
        for (item in items) {
            val total = item.totalQuantity
            val r = reserved[item.itemKey] ?: 0.0
            when {
                item.name in basisNames ->
                    fromStock += item.copy(fromStockText = "in huis")
                total == null -> {
                    val inHouse = r > EPS || pantry.any { it.name == item.name && (it.quantity ?: 0.0) > EPS }
                    if (inHouse) fromStock += item.copy(fromStockText = "in huis") else toBuy += item
                }
                r <= EPS -> toBuy += item
                total - r <= EPS ->
                    fromStock += item.copy(fromStockText = format(total, item.canonicalUnit))
                else -> {
                    val remaining = round(total - r)
                    toBuy += item.copy(
                        totalQuantity = remaining,
                        displayAmount = format(remaining, item.canonicalUnit),
                        fromStockText = format(r, item.canonicalUnit)
                    )
                }
            }
        }
        return StockSplit(toBuy, fromStock)
    }

    data class ParsedInput(val name: String, val unit: String?, val quantity: Double?)

    /** "1 kg rijst" -> rijst, g, 1000. "zout" -> basisvoorraad (geen hoeveelheid). */
    fun parseInput(text: String): ParsedInput? {
        if (text.isBlank()) return null
        val ing = IngredientParser.parse(text)
        val name = ing.name.trim().lowercase()
        if (name.isEmpty()) return null
        return ParsedInput(name, if (ing.quantity != null) ing.unit else null, ing.quantity)
    }

    /** "1,5" of "250" -> getal >= 0, anders null. */
    fun parseAmount(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }

    fun format(quantity: Double, unit: String?): String =
        MealPlannerLogic.formatQuantityForDisplay(quantity, unit)
}
