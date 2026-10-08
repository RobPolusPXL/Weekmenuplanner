package com.example.domain

import com.example.data.web.RecipeFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric zodat org.json echt werkt in een JVM-test.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecipeJsonLdParserTest {

    private fun page(json: String) =
        "<html><head><script type=\"application/ld+json\">$json</script></head><body></body></html>"

    @Test
    fun `leest recept uit losse Recipe node`() {
        val html = page(
            """{"@context":"https://schema.org","@type":"Recipe","name":"Spaghetti bolognese",
               "totalTime":"PT1H15M",
               "recipeIngredient":["400 g gehakt","1 ui","2 el olijfolie"]}"""
        )
        val recipe = RecipeJsonLdParser.parse(html)
        assertNotNull(recipe)
        assertEquals("Spaghetti bolognese", recipe!!.name)
        assertEquals(75, recipe.totalMinutes)
        assertEquals(listOf("400 g gehakt", "1 ui", "2 el olijfolie"), recipe.ingredientLines)
    }

    @Test
    fun `vindt recipe in graph en met type als lijst`() {
        val html = page(
            """{"@context":"https://schema.org","@graph":[
                 {"@type":"WebSite","name":"Site"},
                 {"@type":["Recipe","Thing"],"name":"Stoofvlees",
                  "prepTime":"PT20M","cookTime":"PT2H",
                  "recipeIngredient":["1 kg rundsvlees"]}]}"""
        )
        val recipe = RecipeJsonLdParser.parse(html)!!
        assertEquals("Stoofvlees", recipe.name)
        assertEquals(140, recipe.totalMinutes)
    }

    @Test
    fun `decodeert html entities in ingredienten`() {
        val html = page(
            """[{"@type":"Recipe","name":"Mac &amp; cheese","recipeIngredient":["250 g macaroni","1&#8201;dl melk","1 ui"]}]"""
        )
        val recipe = RecipeJsonLdParser.parse(html)!!
        assertEquals("Mac & cheese", recipe.name)
        assertEquals("250 g macaroni", recipe.ingredientLines[0])
    }

    @Test
    fun `pagina zonder recept geeft null`() {
        assertNull(RecipeJsonLdParser.parse(page("""{"@type":"Article","name":"Nieuws"}""")))
        assertNull(RecipeJsonLdParser.parse("<html><body>geen json-ld</body></html>"))
        assertNull(RecipeJsonLdParser.parse(page("{kapotte json")))
    }

    @Test
    fun `iso duur wordt minuten`() {
        assertEquals(45, RecipeJsonLdParser.parseIsoDurationMinutes("PT45M"))
        assertEquals(60, RecipeJsonLdParser.parseIsoDurationMinutes("PT1H"))
        assertEquals(30, RecipeJsonLdParser.parseIsoDurationMinutes("P0DT0H30M"))
        assertNull(RecipeJsonLdParser.parseIsoDurationMinutes("PT0M"))
        assertNull(RecipeJsonLdParser.parseIsoDurationMinutes("onzin"))
    }

    @Test
    fun `url wordt genormaliseerd naar https`() {
        assertEquals("https://www.libelle-lekker.be/recept", RecipeFetcher.normalizeUrl("www.libelle-lekker.be/recept"))
        assertEquals("https://voorbeeld.be/r", RecipeFetcher.normalizeUrl(" http://voorbeeld.be/r "))
        assertNull(RecipeFetcher.normalizeUrl("geen url"))
        assertNull(RecipeFetcher.normalizeUrl(""))
    }
}
