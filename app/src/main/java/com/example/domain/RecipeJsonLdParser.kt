package com.example.domain

import org.json.JSONArray
import org.json.JSONObject

/** Wat we uit een receptpagina halen. */
data class ImportedRecipe(
    val name: String?,
    val ingredientLines: List<String>,
    val totalMinutes: Int?
)

/**
 * Haalt een recept uit de HTML van een receptpagina via de schema.org/Recipe JSON-LD die vrijwel
 * alle receptsites (Libelle, 15gram, Njam, AH, Allerhande, ...) meeleveren voor Google.
 * Geen netwerk, geen AI: pure tekstverwerking.
 */
object RecipeJsonLdParser {

    private val SCRIPT_REGEX = Regex(
        "<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val DURATION_REGEX = Regex(
        "^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:\\d+S)?)?$",
        RegexOption.IGNORE_CASE
    )

    fun parse(html: String): ImportedRecipe? {
        for (match in SCRIPT_REGEX.findAll(html)) {
            val json = match.groupValues[1].trim()
            if (json.isEmpty()) continue
            val root: Any = try {
                if (json.startsWith("[")) JSONArray(json) else JSONObject(json)
            } catch (_: Exception) {
                continue
            }
            val recipe = findRecipe(root, 0) ?: continue
            val lines = readIngredients(recipe)
            if (lines.isEmpty()) continue
            return ImportedRecipe(
                name = cleanText(recipe.optString("name", "")).ifEmpty { null },
                ingredientLines = lines,
                totalMinutes = readMinutes(recipe)
            )
        }
        return null
    }

    private fun findRecipe(node: Any?, depth: Int): JSONObject? {
        if (depth > 6 || node == null) return null
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findRecipe(node.opt(i), depth + 1)?.let { return it }
                }
            }
            is JSONObject -> {
                if (isRecipeType(node.opt("@type"))) return node
                val keys = node.keys()
                while (keys.hasNext()) {
                    val child = node.opt(keys.next())
                    if (child is JSONObject || child is JSONArray) {
                        findRecipe(child, depth + 1)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun isRecipeType(type: Any?): Boolean = when (type) {
        is String -> type.equals("Recipe", ignoreCase = true)
        is JSONArray -> (0 until type.length()).any { type.optString(it).equals("Recipe", ignoreCase = true) }
        else -> false
    }

    private fun readIngredients(recipe: JSONObject): List<String> {
        val source = recipe.opt("recipeIngredient") ?: recipe.opt("ingredients")
        val raw = when (source) {
            is JSONArray -> (0 until source.length()).map { source.optString(it, "") }
            is String -> source.split('\n')
            else -> emptyList()
        }
        return raw.map { cleanText(it) }.filter { it.isNotEmpty() }
    }

    private fun readMinutes(recipe: JSONObject): Int? {
        parseIsoDurationMinutes(recipe.optString("totalTime", ""))?.let { return it }
        val prep = parseIsoDurationMinutes(recipe.optString("prepTime", ""))
        val cook = parseIsoDurationMinutes(recipe.optString("cookTime", ""))
        val sum = (prep ?: 0) + (cook ?: 0)
        return sum.takeIf { it > 0 }
    }

    /** "PT1H15M" -> 75, "PT45M" -> 45, "P0DT0H30M" -> 30. Geeft null als er geen tijd in zit. */
    fun parseIsoDurationMinutes(text: String): Int? {
        val m = DURATION_REGEX.matchEntire(text.trim()) ?: return null
        val days = m.groupValues[1].toIntOrNull() ?: 0
        val hours = m.groupValues[2].toIntOrNull() ?: 0
        val minutes = m.groupValues[3].toIntOrNull() ?: 0
        return (days * 24 * 60 + hours * 60 + minutes).takeIf { it > 0 }
    }

    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")

    /** Decodeert HTML-entities ("&amp;", "&#039;"), haalt tags weg en klapt witruimte samen. */
    internal fun cleanText(input: String): String {
        var s = input.replace(Regex("<[^>]*>"), " ")
        s = NUMERIC_ENTITY.replace(s) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
        }
        s = s.replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
        return s.replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()
    }
}
