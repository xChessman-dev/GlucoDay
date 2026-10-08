package dev.chessman.glucoday.nutrition

import java.util.Locale

/** Reference values describe 100 g of the edible part in the stated preparation. */
data class Food(
    val id: String,
    val name: String,
    val category: String,
    val carbsPer100g: Double,
    val kcalPer100g: Double,
    val sourceName: String,
    val sourceUrl: String = "",
    val sourceDescription: String = "",
    val custom: Boolean = false,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(carbsPer100g.isFinite() && carbsPer100g in 0.0..100.0)
        require(kcalPer100g.isFinite() && kcalPer100g in 0.0..1000.0)
    }

    fun forGrams(grams: Double): Nutrients {
        require(grams.isFinite() && grams >= 0.0)
        return Nutrients(carbsPer100g * grams / 100.0, kcalPer100g * grams / 100.0)
    }
}

data class Nutrients(val carbsGrams: Double, val kcal: Double) {
    operator fun plus(other: Nutrients) = Nutrients(carbsGrams + other.carbsGrams, kcal + other.kcal)
    operator fun times(factor: Double): Nutrients {
        require(factor.isFinite() && factor >= 0.0)
        return Nutrients(carbsGrams * factor, kcal * factor)
    }
}

data class RecipeIngredient(val foodId: String, val grams: Double) {
    init { require(grams.isFinite() && grams > 0.0) }
}

data class Recipe(
    val id: String,
    val title: String,
    val description: String,
    val minutes: Int,
    val basePortions: Int,
    val ingredients: List<RecipeIngredient>,
    val steps: List<String>,
) {
    init { require(basePortions > 0 && ingredients.isNotEmpty()) }

    fun nutrients(portions: Double = 1.0, foods: Map<String, Food> = FoodCatalog.byId): Nutrients {
        require(portions.isFinite() && portions > 0.0)
        return ingredients.fold(Nutrients(0.0, 0.0)) { total, ingredient ->
            val food = requireNotNull(foods[ingredient.foodId]) { "Unknown food: ${ingredient.foodId}" }
            total + food.forGrams(ingredient.grams)
        } * (portions / basePortions)
    }

    fun ingredientGrams(ingredient: RecipeIngredient, portions: Double): Double {
        require(portions.isFinite() && portions > 0.0)
        return ingredient.grams * portions / basePortions
    }
}

/** Accept Russian decimal commas without accepting exponent, NaN or grouping separators. */
fun parseNutritionNumber(text: String, maximum: Double, allowZero: Boolean = false): Double? {
    val value = text.trim()
    if (!value.matches(Regex("[0-9]+(?:[.,][0-9]{1,2})?"))) return null
    return value.replace(',', '.').toDoubleOrNull()?.takeIf {
        it.isFinite() && it <= maximum && if (allowZero) it >= 0.0 else it > 0.0
    }
}

fun formatNutrition(value: Double): String = String.format(Locale.forLanguageTag("ru-RU"), "%.1f", value)

private fun reference(
    id: String, name: String, category: String, carbs: Double, kcal: Double, description: String,
) = Food(
    id, name, category, carbs, kcal, "USDA · справочное значение",
    "https://fdc.nal.usda.gov/food-search/?query=" + java.net.URLEncoder.encode(description, "UTF-8"),
    description,
)

object FoodCatalog {
    const val referenceNote = "Приблизительные значения на 100 г съедобной части. Состав зависит от сорта, марки и приготовления. Для упакованного продукта используйте его этикетку."
    const val carbohydrateNote = "В справочнике USDA указаны общие углеводы, включая клетчатку. Они могут отличаться от строки «углеводы» на российской этикетке."

    val foods = listOf(
        reference("buckwheat_dry", "Гречка, крупа сухая обжаренная", "Крупы и хлеб", 75.0, 346.0, "Buckwheat groats roasted dry"),
        reference("buckwheat_cooked", "Гречка, варёная на воде", "Крупы и хлеб", 19.9, 92.0, "Buckwheat groats roasted cooked"),
        reference("rice_dry", "Рис белый длиннозёрный, сухой", "Крупы и хлеб", 80.0, 365.0, "Rice white long-grain regular raw unenriched"),
        reference("rice_cooked", "Рис белый длиннозёрный, варёный", "Крупы и хлеб", 28.2, 130.0, "Rice white long-grain regular cooked unenriched without salt"),
        reference("oats_dry", "Овсяные хлопья, сухие", "Крупы и хлеб", 67.7, 379.0, "Cereals oats regular and quick not fortified dry"),
        reference("oats_cooked", "Овсяная каша на воде, без сахара", "Крупы и хлеб", 12.0, 71.0, "Cereals oats regular and quick not fortified cooked with water without salt"),
        reference("pasta_cooked", "Макароны, варёные без масла", "Крупы и хлеб", 30.9, 158.0, "Spaghetti cooked enriched without added salt"),
        reference("bread_whole", "Хлеб цельнозерновой пшеничный", "Крупы и хлеб", 41.3, 247.0, "Bread whole-wheat commercially prepared"),
        reference("potato", "Картофель без кожуры, варёный", "Овощи", 20.1, 87.0, "Potatoes boiled cooked without skin flesh without salt"),
        reference("carrot", "Морковь, сырая", "Овощи", 9.6, 41.0, "Carrots raw"),
        reference("cabbage", "Капуста белокочанная, сырая", "Овощи", 5.8, 25.0, "Cabbage raw"),
        reference("cucumber", "Огурец с кожурой, сырой", "Овощи", 3.6, 15.0, "Cucumber with peel raw"),
        reference("tomato", "Помидор, сырой", "Овощи", 3.9, 18.0, "Tomatoes red ripe raw year round average"),
        reference("broccoli", "Брокколи, варёная без соли", "Овощи", 7.2, 35.0, "Broccoli cooked boiled drained without salt"),
        reference("zucchini", "Кабачок с кожурой, сырой", "Овощи", 3.1, 17.0, "Squash summer zucchini includes skin raw"),
        reference("pepper", "Перец сладкий красный, сырой", "Овощи", 6.0, 31.0, "Peppers sweet red raw"),
        reference("beet", "Свёкла, варёная без соли", "Овощи", 10.0, 44.0, "Beets cooked boiled drained"),
        reference("onion", "Лук репчатый, сырой", "Овощи", 9.3, 40.0, "Onions raw"),
        reference("apple", "Яблоко с кожурой, сырое", "Фрукты и ягоды", 13.8, 52.0, "Apples raw with skin"),
        reference("banana", "Банан, мякоть", "Фрукты и ягоды", 22.8, 89.0, "Bananas raw"),
        reference("pear", "Груша с кожурой, сырая", "Фрукты и ягоды", 15.2, 57.0, "Pears raw"),
        reference("orange", "Апельсин, мякоть", "Фрукты и ягоды", 11.8, 47.0, "Oranges raw all commercial varieties"),
        reference("strawberry", "Клубника, свежая", "Фрукты и ягоды", 7.7, 32.0, "Strawberries raw"),
        reference("raspberry", "Малина, свежая", "Фрукты и ягоды", 11.9, 52.0, "Raspberries raw"),
        reference("grapes", "Виноград, свежий", "Фрукты и ягоды", 18.1, 69.0, "Grapes red or green European type raw"),
        reference("kiwi", "Киви зелёный, мякоть", "Фрукты и ягоды", 14.7, 61.0, "Kiwifruit green raw"),
        reference("milk", "Молоко 2%", "Молочное и яйца", 4.8, 50.0, "Milk reduced fat fluid 2% milkfat with added vitamin A and vitamin D"),
        reference("yogurt", "Йогурт натуральный цельномолочный", "Молочное и яйца", 4.7, 61.0, "Yogurt plain whole milk"),
        reference("cottage", "Творог зернёный 2% (cottage cheese)", "Молочное и яйца", 4.8, 81.0, "Cheese cottage lowfat 2% milkfat"),
        reference("cheddar", "Сыр чеддер", "Молочное и яйца", 1.3, 403.0, "Cheese cheddar"),
        reference("egg", "Яйцо куриное, сваренное вкрутую", "Молочное и яйца", 1.1, 155.0, "Egg whole cooked hard-boiled"),
        reference("chicken", "Куриная грудка без кожи, запечённая", "Мясо и рыба", 0.0, 165.0, "Chicken broilers or fryers breast meat only cooked roasted"),
        reference("salmon", "Лосось атлантический, запечённый", "Мясо и рыба", 0.0, 206.0, "Fish salmon Atlantic farmed cooked dry heat"),
        reference("lentils", "Чечевица, варёная без соли", "Бобовые и орехи", 20.1, 116.0, "Lentils mature seeds cooked boiled without salt"),
        reference("chickpeas", "Нут, варёный без соли", "Бобовые и орехи", 27.4, 164.0, "Chickpeas mature seeds cooked boiled without salt"),
        reference("peas", "Горошек зелёный, варёный", "Бобовые и орехи", 15.6, 84.0, "Peas green cooked boiled drained without salt"),
        reference("walnut", "Грецкий орех, ядра", "Бобовые и орехи", 13.7, 654.0, "Nuts walnuts English"),
        reference("almond", "Миндаль, ядра", "Бобовые и орехи", 21.6, 579.0, "Nuts almonds"),
        reference("olive_oil", "Масло оливковое", "Масла", 0.0, 884.0, "Oil olive salad or cooking"),
        reference("butter", "Масло сливочное несолёное", "Масла", 0.1, 717.0, "Butter without salt"),
    )
    val byId = foods.associateBy { it.id }
}

object RecipeCatalog {
    private fun ingredient(id: String, grams: Int) = RecipeIngredient(id, grams.toDouble())
    val recipes = listOf(
        Recipe("apple_oats", "Овсянка с яблоком", "Тёплый завтрак с молоком", 12, 1,
            listOf(ingredient("oats_dry", 40), ingredient("milk", 150), ingredient("apple", 80)),
            listOf("Отмерьте сухие хлопья и молоко. Сварите кашу по инструкции на упаковке; при необходимости добавьте воду.", "Удалите сердцевину яблока. Взвесьте 80 г съедобной части и добавьте в кашу.")),
        Recipe("berry_yogurt", "Йогурт с ягодами и орехами", "Три ингредиента, без готовки", 5, 1,
            listOf(ingredient("yogurt", 150), ingredient("strawberry", 100), ingredient("walnut", 15)),
            listOf("Промойте ягоды и удалите чашелистики. Отвесьте указанное количество.", "Смешайте с натуральным йогуртом, посыпьте нарезанными ядрами ореха.")),
        Recipe("chicken_buckwheat", "Гречка с курицей", "Гарнир, запечённая грудка и огурец", 10, 1,
            listOf(ingredient("buckwheat_cooked", 150), ingredient("chicken", 100), ingredient("cucumber", 100), ingredient("olive_oil", 5)),
            listOf("Взвесьте уже сваренную гречку и полностью приготовленную куриную грудку без кожи.", "Разогрейте гречку и курицу. Нарежьте огурец и заправьте отмеренным маслом.")),
        Recipe("salmon_rice", "Рис с лососем и брокколи", "Из заранее приготовленных продуктов", 10, 1,
            listOf(ingredient("rice_cooked", 150), ingredient("salmon", 100), ingredient("broccoli", 100)),
            listOf("Взвесьте готовые рис, лосось и брокколи. Для лосося указана масса без кожи и костей.", "Разогрейте и подайте вместе. Если добавляете масло или соус, учтите их отдельно.")),
        Recipe("lentil_salad", "Салат с чечевицей", "Свежие овощи и варёная чечевица", 10, 1,
            listOf(ingredient("lentils", 150), ingredient("tomato", 100), ingredient("cucumber", 100), ingredient("olive_oil", 5)),
            listOf("Взвесьте сваренную чечевицу после слива воды.", "Нарежьте помидор и огурец, смешайте с чечевицей и отмеренным маслом.")),
        Recipe("chickpea_cabbage", "Нут с капустой и морковью", "Хрустящий салат", 10, 1,
            listOf(ingredient("chickpeas", 120), ingredient("cabbage", 100), ingredient("carrot", 50), ingredient("olive_oil", 5)),
            listOf("Используйте готовый варёный нут без жидкости. Нашинкуйте капусту, натрите морковь.", "Смешайте все ингредиенты с отмеренным маслом.")),
        Recipe("egg_toast", "Тост с яйцом и помидором", "Цельнозерновой хлеб и варёное яйцо", 7, 1,
            listOf(ingredient("bread_whole", 40), ingredient("egg", 100), ingredient("tomato", 100)),
            listOf("Отвесьте хлеб до подсушивания и подрумяньте без масла.", "Очистите сваренные вкрутую яйца; взвесьте 100 г. Нарежьте яйца и помидор, подайте на тосте.")),
        Recipe("tomato_pasta", "Паста с помидорами и сыром", "Простой обед за несколько минут", 10, 1,
            listOf(ingredient("pasta_cooked", 150), ingredient("tomato", 150), ingredient("cheddar", 20), ingredient("olive_oil", 5)),
            listOf("Взвесьте уже сваренные макароны после слива воды. Нарежьте помидоры.", "Прогрейте помидоры с отмеренным маслом, смешайте с макаронами и добавьте натёртый сыр.")),
        Recipe("potato_peas", "Картофельный салат с горошком", "С варёным яйцом и маслом", 10, 1,
            listOf(ingredient("potato", 150), ingredient("peas", 80), ingredient("egg", 50), ingredient("olive_oil", 5)),
            listOf("Остудите готовые картофель, горошек и яйцо. Удалите скорлупу, отвесьте съедобные части.", "Нарежьте картофель и яйцо, добавьте горошек и отмеренное масло.")),
        Recipe("cottage_pear", "Зернёный творог с грушей", "С ядрами грецкого ореха", 5, 1,
            listOf(ingredient("cottage", 150), ingredient("pear", 100), ingredient("walnut", 10)),
            listOf("Используйте зернёный творог 2%; для другого продукта пересчитайте по этикетке.", "Удалите сердцевину груши и взвесьте мякоть. Добавьте к творогу с измельчённым орехом.")),
    )
}
