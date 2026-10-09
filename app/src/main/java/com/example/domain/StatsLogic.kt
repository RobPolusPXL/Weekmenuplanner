package com.example.domain

import com.example.data.model.DayKind
import com.example.data.model.DayPlan

/** Statistieken over de ingevulde dagen: per maand of per jaar. */
object StatsLogic {

    data class CountedItem(val label: String, val count: Int)

    data class PeriodStats(
        val cooked: Int,
        val takeaway: Int,
        val reheated: Int,
        val noCook: Int,
        val topDishes: List<CountedItem>,
        val types: List<CountedItem>
    ) {
        val total: Int get() = cooked + takeaway + reheated + noCook
    }

    /**
     * @param periodPrefix "2026" voor een jaar of "2026-10" voor een maand (dateId's zijn yyyy-MM-dd).
     * @param todayIso alleen dagen tot en met vandaag tellen mee: geplande dagen zijn nog niet gegeten.
     */
    fun compute(
        days: Collection<DayPlan>,
        periodPrefix: String,
        todayIso: String,
        topDishLimit: Int = 5
    ): PeriodStats {
        val inPeriod = days.filter { it.dateId.startsWith(periodPrefix) && it.dateId <= todayIso }

        val cookedDays = inPeriod.filter { it.dayKind == DayKind.KOKEN }

        val topDishes = cookedDays
            .groupBy { day ->
                day.dishId ?: day.dishSnapshot?.name?.trim()?.lowercase() ?: "onbekend"
            }
            .map { (_, group) ->
                val label = group.firstNotNullOfOrNull { it.dishSnapshot?.name?.takeIf { n -> n.isNotBlank() } }
                    ?: "Onbekend gerecht"
                CountedItem(label, group.size)
            }
            .sortedWith(compareByDescending<CountedItem> { it.count }.thenBy { it.label.lowercase() })
            .take(topDishLimit)

        val types = cookedDays
            .mapNotNull { it.dishSnapshot?.type?.trim()?.lowercase()?.takeIf { t -> t.isNotEmpty() } }
            .groupBy { it }
            .map { (type, group) -> CountedItem(type, group.size) }
            .sortedWith(compareByDescending<CountedItem> { it.count }.thenBy { it.label })

        return PeriodStats(
            cooked = cookedDays.size,
            takeaway = inPeriod.count { it.dayKind == DayKind.AFHAAL },
            reheated = inPeriod.count { it.dayKind == DayKind.OPWARM },
            noCook = inPeriod.count { it.dayKind == DayKind.NIET_KOKEN },
            topDishes = topDishes,
            types = types
        )
    }
}
