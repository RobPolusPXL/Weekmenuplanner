package com.example.data.ai

import com.example.data.model.IngredientItem
import com.example.domain.IngredientParser

/**
 * Zet ingrediëntregels zoals "150 gram gehakt" om in naam, hoeveelheid en eenheid.
 *
 * Dit gebeurt volledig lokaal via [IngredientParser]: geen Gemini, geen internet, geen quota.
 * De naam van dit object en de parameters blijven gelijk zodat de rest van de app niet wijzigt.
 */
object IngredientNormalizerService {

    @Suppress("UNUSED_PARAMETER")
    suspend fun normalizeIngredients(
        rawLines: List<String>,
        isOnline: Boolean = true
    ): List<IngredientItem> {
        return rawLines
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { IngredientParser.parse(it) }
    }
}
