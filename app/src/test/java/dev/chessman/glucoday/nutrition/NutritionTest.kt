package dev.chessman.glucoday.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NutritionTest {
    @Test fun weighsAnEdiblePortionWithoutRoundingIntermediateValues() {
        val apple = FoodCatalog.byId.getValue("apple")
        val portion = apple.forGrams(125.5)
        assertEquals(17.319, portion.carbsGrams, 0.0000001)
        assertEquals(65.26, portion.kcal, 0.0000001)
        assertEquals(Nutrients(0.0, 0.0), apple.forGrams(0.0))
    }

    @Test fun acceptsDecimalCommaAndDotAndRejectsAmbiguousMass() {
        assertEquals(125.5, parseNutritionNumber(" 125,5 ", 5000.0)!!, 0.0)
        assertEquals(0.25, parseNutritionNumber("0.25", 20.0)!!, 0.0)
        assertEquals(5000.0, parseNutritionNumber("5000", 5000.0)!!, 0.0)
        listOf("", " ", "0", "-1", "NaN", "Infinity", "1e3", "1 000", "1,2.3", "2,", ".5", "1.234", "5000.01")
            .forEach { assertNull("Unexpected valid value: $it", parseNutritionNumber(it, 5000.0)) }
    }

    @Test fun zeroCarbohydratesAreValidForAnEnteredLabel() {
        assertEquals(0.0, parseNutritionNumber("0", 100.0, allowZero = true)!!, 0.0)
        assertNull(parseNutritionNumber("100.1", 100.0, allowZero = true))
        assertNull(parseNutritionNumber("1001", 1000.0, allowZero = true))
    }

    @Test fun recipeTotalsUseEachIngredientsMass() {
        val breakfast = RecipeCatalog.recipes.first { it.id == "apple_oats" }
        val nutrients = breakfast.nutrients()
        // 40 g oats + 150 g milk + 80 g apple: 27.08 + 7.2 + 11.04 g carbs.
        assertEquals(45.32, nutrients.carbsGrams, 0.0000001)
        assertEquals(268.2, nutrients.kcal, 0.0000001)
        assertEquals(22.66, breakfast.nutrients(0.5).carbsGrams, 0.0000001)
        assertEquals(536.4, breakfast.nutrients(2.0).kcal, 0.0000001)
        assertEquals(20.0, breakfast.ingredientGrams(breakfast.ingredients.first(), 0.5), 0.0)
    }

    @Test fun dividesBatchRecipeIntoTheRequestedNumberOfPortions() {
        val batch = Recipe("test", "Яблоки", "", 1, 4,
            listOf(RecipeIngredient("apple", 400.0)), emptyList())
        assertEquals(52.0, batch.nutrients(1.0).kcal, 0.0000001)
        assertEquals(27.6, batch.nutrients(2.0).carbsGrams, 0.0000001)
        assertEquals(50.0, batch.ingredientGrams(batch.ingredients.single(), 0.5), 0.0)
    }

    @Test fun rejectsMissingIngredientsInsteadOfSilentlyUndercounting() {
        val broken = Recipe("missing", "", "", 1, 1,
            listOf(RecipeIngredient("not-in-catalog", 100.0)), emptyList())
        rejects { broken.nutrients() }
    }

    @Test fun refusesNegativeOrNonFiniteDomainValues() {
        val apple = FoodCatalog.byId.getValue("apple")
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            rejects { apple.forGrams(value) }
            rejects { RecipeCatalog.recipes.first().nutrients(value) }
            rejects { RecipeIngredient("apple", value) }
        }
        rejects { RecipeCatalog.recipes.first().nutrients(0.0) }
        rejects { Food("id", "name", "category", Double.NaN, 50.0, "source") }
        rejects { Food("id", "name", "category", 101.0, 50.0, "source") }
    }

    @Test fun catalogAndRecipesAreCompleteAndReferenceOnlyExistingIngredients() {
        assertEquals(40, FoodCatalog.foods.size)
        assertEquals(FoodCatalog.foods.size, FoodCatalog.byId.size)
        assertEquals(10, RecipeCatalog.recipes.size)
        FoodCatalog.foods.forEach {
            assertTrue(it.sourceUrl.startsWith("https://fdc.nal.usda.gov/"))
            assertTrue(it.sourceDescription.isNotBlank())
        }
        RecipeCatalog.recipes.forEach {
            assertTrue(it.steps.isNotEmpty())
            assertTrue(it.nutrients().kcal > 0.0)
        }
        assertTrue(FoodCatalog.byId.getValue("rice_dry").carbsPer100g > FoodCatalog.byId.getValue("rice_cooked").carbsPer100g)
        assertTrue(FoodCatalog.byId.getValue("buckwheat_dry").carbsPer100g > FoodCatalog.byId.getValue("buckwheat_cooked").carbsPer100g)
    }

    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected IllegalArgumentException") } catch (_: IllegalArgumentException) { }
    }
}
