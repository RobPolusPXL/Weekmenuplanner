package com.example.data.ai

import android.util.Log
import com.example.data.model.IngredientItem
import com.example.domain.MealPlannerLogic
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Deel B: System instruction & Gemini normalisatie van ingrediënten uitsluitend via Firebase AI Logic
 * (geen API-sleutel in de client).
 */
object IngredientNormalizerService {

    private const val TAG = "IngredientNormalizer"
    private const val MODEL_NAME = "gemini-flash-latest"

    const val SYSTEM_INSTRUCTION = """Je bent een parser voor ingrediëntregels in Belgisch Nederlands. Je krijgt een JSON-array met strings, elk één ingrediëntregel zoals een gebruiker die typte. Je geeft voor elke regel één genormaliseerd object terug, in dezelfde volgorde en met hetzelfde aantal elementen als de invoer. Je antwoordt uitsluitend met een geldige JSON-array, zonder tekst eromheen.

Uitvoer per regel:
{ "name": string, "quantity": number | null, "unit": string | null }

Regels:
1. name: enkelvoud, lowercase, zonder hoeveelheid, zonder bereidingswijze en zonder bijvoeglijke bijzaken. "2 fijngesneden uien" geeft name "ui". "verse basilicum" geeft "basilicum". Behoud woorden die een ander product maken: "rode ui", "kippenbouillon", "volle melk". Bereidingswoorden zoals gemalen, geraspt of fijngesneden vallen weg. Bij twijfel: behoud het woord enkel als het product anders is bij de winkel (rode ui ≠ ui, bloemkool ≠ kool).
2. Meervoud naar enkelvoud: "tomaten" geeft "tomaat", "aardappelen" geeft "aardappel", "eieren" geeft "ei". Spellingvarianten naar één vorm: "patatten" geeft "aardappel", "look" en "knoflook" geven "knoflook".
3. quantity: een getal. Breuken en komma's omzetten: "1/2" wordt 0.5, "1,5" wordt 1.5. Bereiken ("2-3") nemen de bovengrens. Geen hoeveelheid in de regel: null. Verzin nooit een hoeveelheid. "een beetje", "naar smaak" en "snuf" geven quantity null.
4. unit: één van "g", "ml", "stuk", "el", "tl", "blik", "pak", "bos", "teen", "plak", "snee", "pot", "zakje". Omrekenen vóór uitvoer: kg naar g (×1000), l naar ml (×1000), dl naar ml (×100), cl naar ml (×10). Telbare dingen zonder eenheid ("2 uien", "3 eieren") krijgen unit "stuk". Staat er geen hoeveelheid, dan is unit null.
5. Bevat een regel meerdere ingrediënten ("zout en peper"), geef het eerste terug en zet de rest niet apart.
6. Onleesbare of lege regel: { "name": <de regel lowercase getrimd>, "quantity": null, "unit": null }.
7. Geen vertalingen, geen merknamen toevoegen, geen commentaar.

Voorbeeld:
Invoer: ["2 uien", "500 g gehakt", "1,5 kg patatten", "1/2 bloemkool", "snuf zout", "2 el olijfolie", "3 teentjes look", "1 blik tomatenblokjes", "250 ml room", "peper"]
Uitvoer: [{"name":"ui","quantity":2,"unit":"stuk"},{"name":"gehakt","quantity":500,"unit":"g"},{"name":"aardappel","quantity":1500,"unit":"g"},{"name":"bloemkool","quantity":0.5,"unit":"stuk"},{"name":"zout","quantity":null,"unit":null},{"name":"olijfolie","quantity":2,"unit":"el"},{"name":"knoflook","quantity":3,"unit":"teen"},{"name":"tomatenblokjes","quantity":1,"unit":"blik"},{"name":"room","quantity":250,"unit":"ml"},{"name":"peper","quantity":null,"unit":null}]"""

    private val responseSchema: Schema by lazy {
        val itemSchema = Schema.obj(
            properties = mapOf(
                "name" to Schema.string(nullable = false),
                "quantity" to Schema.double(nullable = true),
                "unit" to Schema.string(nullable = true)
            )
        )
        Schema.array(items = itemSchema)
    }

    /**
     * Eén call per opgeslagen gerecht met alle raw-regels in één JSON-array via Firebase AI Logic.
     * Is het aantal elementen in het antwoord niet gelijk aan de invoer, of is het toestel offline / faalt de call,
     * dan wordt het antwoord verworpen en wordt de fallback uit 6.6 gebruikt
     * (name = lowercase getrimde raw, quantity = null, unit = null, needsNormalization = true).
     */
    suspend fun normalizeIngredients(
        rawLines: List<String>,
        isOnline: Boolean
    ): List<IngredientItem> = withContext(Dispatchers.IO) {
        val cleanedLines = rawLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanedLines.isEmpty()) return@withContext emptyList()

        if (!isOnline) {
            return@withContext cleanedLines.map { MealPlannerLogic.createOfflineFallbackIngredient(it) }
        }

        try {
            val result = callFirebaseAiLogic(cleanedLines)
            if (result != null && result.size == cleanedLines.size) {
                return@withContext result
            }
            Log.w(TAG, "Aantal elementen in Gemini-antwoord wijkt af of antwoord leeg; fallback 6.6 gebruikt.")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI Logic normalisatie mislukt, fallback 6.6 gebruikt: ${e.message}")
        }

        cleanedLines.map { MealPlannerLogic.createOfflineFallbackIngredient(it) }
    }

    private suspend fun callFirebaseAiLogic(rawLines: List<String>): List<IngredientItem>? {
        val config = GenerationConfig.builder().apply {
            temperature = 0f
            responseMimeType = "application/json"
            this.responseSchema = this@IngredientNormalizerService.responseSchema
        }.build()

        val model = FirebaseAI.getInstance(
            FirebaseApp.getInstance(),
            GenerativeBackend.googleAI()
        ).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = config,
            systemInstruction = content { text(SYSTEM_INSTRUCTION) }
        )

        val inputJsonArray = JSONArray(rawLines).toString()
        val response = model.generateContent(inputJsonArray)
        val text = response.text ?: return null
        return parseNormalizedJson(text, rawLines)
    }

    internal fun parseNormalizedJson(jsonText: String, originalLines: List<String>): List<IngredientItem>? {
        val cleanedText = jsonText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val array = JSONArray(cleanedText)
        // Verwerp het antwoord als het aantal elementen niet exact gelijk is aan de invoer
        if (array.length() != originalLines.size) return null

        val result = mutableListOf<IngredientItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: return null
            val raw = originalLines[i]
            val name = obj.optString("name", "").trim().lowercase().ifEmpty { raw.trim().lowercase() }
            val hasQty = !obj.isNull("quantity")
            val rawQty = if (hasQty) {
                obj.optDouble("quantity", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
            } else {
                null
            }
            val rawUnit = if (!obj.isNull("unit") && rawQty != null) {
                obj.optString("unit", "").trim().lowercase().takeIf { it.isNotEmpty() && it != "null" }
            } else {
                null
            }

            val converted = MealPlannerLogic.convertToCanonicalUnit(rawQty, rawUnit)
            result.add(
                IngredientItem(
                    raw = raw,
                    name = name,
                    quantity = converted.quantity,
                    unit = if (converted.quantity != null) converted.canonicalUnit else null,
                    needsNormalization = false
                )
            )
        }
        return result
    }
}
