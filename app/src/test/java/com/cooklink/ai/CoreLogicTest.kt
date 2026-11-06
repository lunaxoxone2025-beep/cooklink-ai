package com.cooklink.ai

import com.cooklink.ai.data.GroceryCategories
import com.cooklink.ai.data.Units
import com.cooklink.ai.data.ai.AiException
import com.cooklink.ai.data.ai.RecipeExtraction
import com.cooklink.ai.model.Certainty
import com.cooklink.ai.model.Origin
import com.cooklink.ai.model.formatMinutes
import com.cooklink.ai.model.formatQuantity
import com.cooklink.ai.ui.screens.RecipeEditorViewModel
import com.cooklink.ai.ui.screens.extractUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattingTest {
    @Test fun fractions() {
        assertEquals("½", formatQuantity(0.5))
        assertEquals("1¼", formatQuantity(1.25))
        assertEquals("2", formatQuantity(2.0))
        assertEquals("⅓", formatQuantity(1.0 / 3))
        assertEquals("3", formatQuantity(2.97))
        assertEquals("", formatQuantity(null))
    }

    @Test fun minutes() {
        assertEquals("45 min", formatMinutes(45))
        assertEquals("2 h", formatMinutes(120))
        assertEquals("1 h 30 min", formatMinutes(90))
        assertEquals("—", formatMinutes(null))
    }
}

class ParsingTest {
    @Test fun quantities() {
        assertEquals(0.5, RecipeEditorViewModel.parseNumber("1/2")!!, 1e-9)
        assertEquals(1.5, RecipeEditorViewModel.parseNumber("1 1/2")!!, 1e-9)
        assertEquals(1.5, RecipeEditorViewModel.parseNumber("1,5")!!, 1e-9)
        assertEquals(0.5, RecipeEditorViewModel.parseNumber("½")!!, 1e-9)
        assertNull(RecipeEditorViewModel.parseNumber("some"))
        assertNull(RecipeEditorViewModel.parseNumber(""))
    }

    @Test fun sharedLinks() {
        assertEquals("https://youtu.be/abc123", extractUrl("Look at this! https://youtu.be/abc123."))
        assertNull(extractUrl("no link here"))
    }
}

class ExtractionTest {
    private val reply = """
        ```json
        {"isRecipe": true, "title": "Pancakes", "servings": 4,
         "ingredients": [
           {"name": "flour", "quantity": 200, "unit": "g", "certainty": "confirmed"},
           {"name": "milk", "quantity": 300, "unit": "ml", "certainty": "missing"},
           {"name": "salt", "quantity": 1, "unit": "pinch", "certainty": "guess"}
         ],
         "steps": [{"text": "Mix", "durationMinutes": 2.4, "certainty": "confirmed"}]}
        ```
    """.trimIndent()

    @Test fun parsesAndKeepsHonesty() {
        val r = RecipeExtraction.toRecipe(RecipeExtraction.parse(reply), Origin.TEXT, emptyList(), null)
        assertEquals("Pancakes", r.title)
        assertEquals(4, r.servings)
        assertEquals(Certainty.CONFIRMED, r.ingredients[0].certainty)
        // A "missing" amount never keeps a number, whatever the model sent.
        assertNull(r.ingredients[1].quantity)
        assertEquals(Certainty.MISSING, r.ingredients[1].certainty)
        // Unknown certainty labels are treated as estimates, never as confirmed.
        assertEquals(Certainty.ESTIMATED, r.ingredients[2].certainty)
        assertEquals(2, r.steps[0].durationMinutes)
        assertEquals("Pancakes", r.original?.title)
    }

    @Test(expected = AiException::class)
    fun notARecipe() {
        RecipeExtraction.toRecipe(RecipeExtraction.parse("""{"isRecipe": false, "reason": "a cat video"}"""), Origin.URL, emptyList(), null)
    }

    @Test(expected = AiException::class)
    fun garbage() {
        RecipeExtraction.parse("sorry, I can't")
    }
}

class ShoppingTest {
    @Test fun unitMerging() {
        assertTrue(Units.compatible("g", "kg"))
        assertTrue(Units.compatible("ml", "L"))
        assertTrue(!Units.compatible("g", "ml"))
        val (q, u) = Units.add(800.0, "g", 0.5, "kg")
        assertEquals(1.3, q!!, 1e-9)
        assertEquals("kg", u)
        val (q2, u2) = Units.add(2.0, "tbsp", 3.0, "tsp")
        assertEquals(3.0, q2!!, 1e-9)
        assertEquals("tbsp", u2)
    }

    @Test fun categories() {
        assertEquals("Produce", GroceryCategories.guess("Red onion"))
        assertEquals("Dairy & Eggs", GroceryCategories.guess("Eggs"))
        assertEquals("Spices", GroceryCategories.guess("black pepper"))
    }
}
