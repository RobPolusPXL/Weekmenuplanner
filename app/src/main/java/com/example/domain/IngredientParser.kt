package com.example.domain

import com.example.data.model.IngredientItem

/**
 * Eenvoudige, lokale parser voor ingrediëntregels. Geen internet en geen AI nodig.
 *
 * Voorbeelden:
 *  "150 gram gehakt"   -> naam "gehakt",      hoeveelheid 150,  eenheid "g"
 *  "1,5 kg patatten"   -> naam "aardappel",   hoeveelheid 1500, eenheid "g"
 *  "2 uien"            -> naam "ui",          hoeveelheid 2,    eenheid "stuk"
 *  "snuf zout"         -> naam "zout",        geen hoeveelheid
 *  "peper"             -> naam "peper",       geen hoeveelheid
 *
 * Het omrekenen van eenheden (kg naar g, l naar ml, ...) gebeurt door
 * [MealPlannerLogic.convertToCanonicalUnit], zodat de boodschappenlijst dezelfde eenheden gebruikt.
 */
object IngredientParser {

    /** Alle woorden die als eenheid herkend worden (direct na het getal). */
    private val UNIT_WORDS = setOf(
        "kg", "kilogram", "kilo", "g", "gr", "gram",
        "l", "liter", "dl", "deciliter", "cl", "centiliter", "ml", "milliliter",
        "stuk", "stuks", "st",
        "el", "eetlepel", "eetlepels",
        "tl", "kl", "theelepel", "theelepels", "koffielepel",
        "teen", "tenen", "teentje", "teentjes",
        "blik", "blikken", "blikje",
        "pak", "pakken", "pakje",
        "bos", "bosje", "bosjes", "bussel",
        "plak", "plakken", "plakje", "plakjes",
        "snee", "sneden", "sneetje", "sneetjes",
        "pot", "potten", "potje", "potjes",
        "zak", "zakje", "zakjes"
    )

    /** Woorden zonder echte hoeveelheid: "snuf zout" wordt gewoon "zout". */
    private val NO_QUANTITY_WORDS = setOf(
        "snuf", "snufje", "scheut", "scheutje", "beetje", "handje", "handvol", "mespuntje"
    )

    /** Meervoud naar enkelvoud, zodat "2 uien" en "1 ui" samengeteld worden. */
    private val SINGULARS = mapOf(
        "uien" to "ui",
        "tomaten" to "tomaat",
        "aardappelen" to "aardappel",
        "aardappels" to "aardappel",
        "patatten" to "aardappel",
        "eieren" to "ei",
        "eitjes" to "ei",
        "wortelen" to "wortel",
        "wortels" to "wortel",
        "paprika's" to "paprika",
        "paprikas" to "paprika",
        "champignons" to "champignon",
        "courgettes" to "courgette",
        "preien" to "prei",
        "citroenen" to "citroen",
        "appels" to "appel",
        "appelen" to "appel",
        "bananen" to "banaan",
        "komkommers" to "komkommer",
        "sjalotten" to "sjalot",
        "look" to "knoflook"
    )

    // getal (1, 1,5, 1.5 of 1/2), optioneel een woord direct erna (de eenheid), en de rest
    private val LEADING_NUMBER = Regex(
        "^(\\d+(?:[.,]\\d+)?(?:\\s*/\\s*\\d+)?)\\s*(\\p{L}[\\p{L}']*)?\\s*(.*)$",
        RegexOption.DOT_MATCHES_ALL
    )
    private val PARENTHESES = Regex("\\([^)]*\\)")
    private val WHITESPACE = Regex("\\s+")

    fun parse(rawLine: String): IngredientItem {
        val raw = rawLine.trim()
        val cleaned = raw.lowercase()
            .replace(PARENTHESES, " ")
            .replace("naar smaak", " ")
            .replace(WHITESPACE, " ")
            .trim()

        var quantity: Double? = null
        var unitWord: String? = null
        var nameText = cleaned

        val match = LEADING_NUMBER.matchEntire(cleaned)
        val number = match?.let { parseNumber(it.groupValues[1]) }
        if (match != null && number != null) {
            quantity = number
            val word = match.groups[2]?.value
            val rest = match.groupValues[3].trim()
            if (word != null && word in UNIT_WORDS) {
                unitWord = word
                nameText = rest
            } else {
                // Geen eenheid, bv. "2 uien" of "2 rode uien": tel gewoon stuks.
                unitWord = "stuk"
                nameText = listOfNotNull(word, rest.ifEmpty { null }).joinToString(" ")
            }
        } else {
            val words = cleaned.split(" ")
            if (words.size > 1 && words[0] in NO_QUANTITY_WORDS) {
                nameText = words.drop(1).joinToString(" ")
            }
        }

        val name = singularizeLastWord(
            nameText.removePrefix("van ").removePrefix("aan ").trim().trimEnd(',', '.', ';')
        )

        // Niets bruikbaars als naam (bv. "150 gram"): bewaar de tekst zoals getypt.
        if (name.isEmpty()) {
            return IngredientItem(raw = raw, name = cleaned.ifEmpty { raw }, needsNormalization = false)
        }

        val unitForConversion = if (unitWord == "kilo") "kg" else unitWord
        val converted = MealPlannerLogic.convertToCanonicalUnit(quantity, unitForConversion)
        return IngredientItem(
            raw = raw,
            name = name,
            quantity = converted.quantity,
            unit = if (converted.quantity != null) converted.canonicalUnit else null,
            needsNormalization = false
        )
    }

    private fun parseNumber(text: String): Double? {
        val t = text.replace(" ", "")
        if ('/' in t) {
            val parts = t.split('/')
            val numerator = parts[0].replace(',', '.').toDoubleOrNull()
            val denominator = parts.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
            if (numerator == null || denominator == null || denominator == 0.0) return null
            return numerator / denominator
        }
        return t.replace(',', '.').toDoubleOrNull()
    }

    private fun singularizeLastWord(name: String): String {
        if (name.isEmpty()) return name
        val words = name.split(" ")
        val last = words.last()
        val singular = SINGULARS[last] ?: return name
        return (words.dropLast(1) + singular).joinToString(" ")
    }
}
