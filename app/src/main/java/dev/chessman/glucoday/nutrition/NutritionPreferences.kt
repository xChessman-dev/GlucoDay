package dev.chessman.glucoday.nutrition

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small user-owned food catalog; no diary or health records are stored here. */
internal class NutritionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("glucoday_nutrition", Context.MODE_PRIVATE)

    fun customFoods(): List<Food> = runCatching {
        val array = JSONArray(preferences.getString("custom_foods", "[]"))
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                Food(
                    id = item.getString("id"), name = item.getString("name"), category = "Мои продукты",
                    carbsPer100g = item.getDouble("carbs"), kcalPer100g = item.getDouble("kcal"),
                    sourceName = "Введено вами по этикетке", custom = true,
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun saveCustomFoods(foods: List<Food>) {
        val values = JSONArray()
        foods.forEach { food ->
            values.put(JSONObject().put("id", food.id).put("name", food.name)
                .put("carbs", food.carbsPer100g).put("kcal", food.kcalPer100g))
        }
        preferences.edit().putString("custom_foods", values.toString()).apply()
    }

    fun favorites(): Set<String> = preferences.getStringSet("favorites", emptySet())?.toSet().orEmpty()

    fun saveFavorites(ids: Set<String>) {
        preferences.edit().putStringSet("favorites", ids.toSet()).apply()
    }
}
