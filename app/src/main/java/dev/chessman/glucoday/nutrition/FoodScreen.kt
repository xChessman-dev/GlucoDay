package dev.chessman.glucoday.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun FoodScreen(
    onLogMeal: (title: String, carbsGrams: Double, kcal: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember(context) { NutritionPreferences(context) }
    var customFoods by remember { mutableStateOf(preferences.customFoods()) }
    var favorites by remember { mutableStateOf(preferences.favorites()) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var selectedFoodId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    val foods = remember(customFoods) { customFoods + FoodCatalog.foods }
    val search = query.trim().lowercase().replace('ё', 'е')
    val filteredFoods = remember(foods, search, favorites, favoritesOnly) {
        foods.filter { food ->
            (!favoritesOnly || food.id in favorites) &&
                (food.name + " " + food.category).lowercase().replace('ё', 'е').contains(search)
        }
    }
    val filteredRecipes = remember(search, favorites, favoritesOnly) {
        RecipeCatalog.recipes.filter { recipe ->
            (!favoritesOnly || recipe.id in favorites) &&
                (recipe.title + " " + recipe.description + " " + recipe.ingredients.joinToString { FoodCatalog.byId.getValue(it.foodId).name })
                    .lowercase().replace('ё', 'е').contains(search)
        }
    }
    val toggleFavorite: (String) -> Unit = { id ->
        favorites = if (id in favorites) favorites - id else favorites + id
        preferences.saveFavorites(favorites)
    }
    val log: (String, Nutrients) -> Unit = { title, nutrients ->
        onLogMeal(title, nutrients.carbsGrams, nutrients.kcal)
        selectedFoodId = null
        selectedRecipeId = null
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Питание", style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        Text("Состав порции — под рукой", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showSources = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Info, "Источники и точность значений")
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("От продукта к порции", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Найдите продукт или рецепт, укажите количество и сохраните приём пищи.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("40 продуктов  ·  10 рецептов  ·  доступны офлайн", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    listOf("Продукты", "Рецепты").forEachIndexed { index, title ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(if (tab == 0) "Найти продукт" else "Рецепт или ингредиент") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) ({
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Close, "Очистить поиск")
                        }
                    }) else null,
                    shape = RoundedCornerShape(18.dp),
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text("Избранное") }, modifier = Modifier.heightIn(min = 48.dp),
                        leadingIcon = { Icon(Icons.Outlined.StarBorder, null, Modifier.size(18.dp)) })
                    Spacer(Modifier.weight(1f))
                    Text(if (tab == 0) "${filteredFoods.size} продуктов" else "${filteredRecipes.size} рецептов",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tab == 0) {
                item {
                    OutlinedButton(onClick = { showCustom = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
                        Text("Добавить свой продукт", Modifier.padding(start = 8.dp))
                    }
                }
                if (filteredFoods.isEmpty()) item {
                    FoodEmptyState(favoritesOnly, query, onReset = { query = ""; favoritesOnly = false })
                }
                items(filteredFoods, key = { it.id }) { food ->
                    NutritionCard(
                        title = food.name, subtitle = food.category,
                        detail = "${formatNutrition(food.carbsPer100g)} г углеводов · ${food.kcalPer100g.toInt()} ккал / 100 г",
                        favorite = food.id in favorites, onFavorite = { toggleFavorite(food.id) },
                        onClick = { selectedFoodId = food.id },
                    )
                }
            } else {
                item {
                    Text("Время указано для сборки из готовых ингредиентов, если не описано иначе.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (filteredRecipes.isEmpty()) item {
                    FoodEmptyState(favoritesOnly, query, onReset = { query = ""; favoritesOnly = false })
                }
                items(filteredRecipes, key = { it.id }) { recipe ->
                    val nutrients = recipe.nutrients()
                    NutritionCard(
                        title = recipe.title, subtitle = "${recipe.minutes} мин · ${recipe.description}",
                        detail = "${formatNutrition(nutrients.carbsGrams)} г углеводов · ${formatNutrition(nutrients.kcal)} ккал / порция",
                        favorite = recipe.id in favorites, onFavorite = { toggleFavorite(recipe.id) },
                        onClick = { selectedRecipeId = recipe.id },
                    )
                }
            }
            item {
                Text("Справочные значения приблизительны. Сверяйте продукты с этикеткой.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    foods.firstOrNull { it.id == selectedFoodId }?.let { food ->
        FoodPortionSheet(food, onDismiss = { selectedFoodId = null }, onLog = log)
    }
    RecipeCatalog.recipes.firstOrNull { it.id == selectedRecipeId }?.let { recipe ->
        RecipeSheet(recipe, onDismiss = { selectedRecipeId = null }, onLog = log)
    }
    if (showCustom) CustomFoodSheet(onDismiss = { showCustom = false }) { food ->
        customFoods = customFoods + food
        preferences.saveCustomFoods(customFoods)
        showCustom = false
        tab = 0
        query = ""
        favoritesOnly = false
        selectedFoodId = food.id
    }
    if (showSources) SourcesSheet(onDismiss = { showSources = false })
}

@Composable
private fun NutritionCard(title: String, subtitle: String, detail: String, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        if (favorite) "Убрать $title из избранного" else "В избранное: $title",
                        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FoodEmptyState(favoritesOnly: Boolean, query: String, onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (favoritesOnly) Icons.Outlined.FavoriteBorder else Icons.Outlined.Search, null,
            Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (favoritesOnly && query.isBlank()) "Здесь будет ваше избранное" else "Ничего не найдено", style = MaterialTheme.typography.titleMedium)
        Text(if (favoritesOnly && query.isBlank()) "Нажмите на звезду рядом с продуктом или рецептом." else "Попробуйте другое название или добавьте свой продукт.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) { Text("Показать всё") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NutritionSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "Закрыть") }
            }
            content()
        }
    }
}

@Composable
private fun NutrientSummary(nutrients: Nutrients, caption: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("${formatNutrition(nutrients.carbsGrams)} г углеводов", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("${formatNutrition(nutrients.kcal)} ккал", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun FoodPortionSheet(food: Food, onDismiss: () -> Unit, onLog: (String, Nutrients) -> Unit) {
    var gramsText by rememberSaveable(food.id) { mutableStateOf("100") }
    var attempted by rememberSaveable(food.id) { mutableStateOf(false) }
    val massFocus = remember { FocusRequester() }
    val grams = parseNutritionNumber(gramsText, 3000.0)
    val uriHandler = LocalUriHandler.current
    NutritionSheet(food.name, onDismiss) {
        Text(food.category, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = gramsText, onValueChange = { gramsText = it }, modifier = Modifier.fillMaxWidth().focusRequester(massFocus),
            label = { Text("Масса порции, г") }, singleLine = true, isError = attempted && grams == null,
            supportingText = { Text(if (attempted && grams == null) "Введите массу больше 0 и до 3000 г, например 125,5." else "Можно использовать запятую, например 125,5.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (grams != null) NutrientSummary(food.forGrams(grams), "В порции ${formatNutrition(grams)} г")
        Button(onClick = {
            attempted = true
            if (grams != null) onLog("${food.name} · ${formatNutrition(grams)} г", food.forGrams(grams))
            else massFocus.requestFocus()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Добавить в дневник") }
        Text(if (food.custom) food.sourceName else FoodCatalog.referenceNote,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!food.custom) {
            Text(FoodCatalog.carbohydrateNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { runCatching { uriHandler.openUri(food.sourceUrl) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Источник: USDA FoodData Central")
            }
            Text(food.sourceDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecipeSheet(recipe: Recipe, onDismiss: () -> Unit, onLog: (String, Nutrients) -> Unit) {
    var portionsText by rememberSaveable(recipe.id) { mutableStateOf("1") }
    var attempted by rememberSaveable(recipe.id) { mutableStateOf(false) }
    val portionsFocus = remember { FocusRequester() }
    val portions = parseNutritionNumber(portionsText, 20.0)
    NutritionSheet(recipe.title, onDismiss) {
        Text("${recipe.minutes} мин · ${recipe.description}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = portionsText, onValueChange = { portionsText = it }, modifier = Modifier.fillMaxWidth().focusRequester(portionsFocus),
            label = { Text("Количество порций") }, singleLine = true, isError = attempted && portions == null,
            supportingText = { Text(if (attempted && portions == null) "Введите число больше 0 и до 20, например 0,5." else "Для половины порции введите 0,5.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (portions != null) {
            NutrientSummary(recipe.nutrients(portions), "На выбранное количество порций")
            Text("Ингредиенты", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            recipe.ingredients.forEach { ingredient ->
                val food = FoodCatalog.byId.getValue(ingredient.foodId)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(food.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${formatNutrition(recipe.ingredientGrams(ingredient, portions))} г",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        HorizontalDivider()
        Text("Как приготовить", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        recipe.steps.forEachIndexed { index, step -> Text("${index + 1}. $step", style = MaterialTheme.typography.bodyLarge) }
        Text("Расчёт — сумма ингредиентов из справочника. Указана их масса в описанном состоянии, а не вес готового блюда. Масло, соусы и замены меняют состав.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(FoodCatalog.carbohydrateNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {
            attempted = true
            if (portions != null) onLog("${recipe.title} · ${formatNutrition(portions)} порц.", recipe.nutrients(portions))
            else portionsFocus.requestFocus()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Добавить в дневник") }
    }
}

@Composable
private fun CustomFoodSheet(onDismiss: () -> Unit, onSave: (Food) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var carbsText by rememberSaveable { mutableStateOf("") }
    var kcalText by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val carbsFocus = remember { FocusRequester() }
    val kcalFocus = remember { FocusRequester() }
    val carbs = parseNutritionNumber(carbsText, 100.0, allowZero = true)
    val kcal = parseNutritionNumber(kcalText, 1000.0, allowZero = true)
    NutritionSheet("Свой продукт", onDismiss) {
        Text("Перенесите значения с упаковки на 100 г. Продукт сохранится на этом устройстве.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = name, onValueChange = { name = it.take(100) }, modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            label = { Text("Название продукта") }, isError = attempted && name.isBlank(),
            supportingText = if (attempted && name.isBlank()) ({ Text("Введите название продукта.") }) else null)
        OutlinedTextField(value = carbsText, onValueChange = { carbsText = it }, modifier = Modifier.fillMaxWidth().focusRequester(carbsFocus),
            label = { Text("Углеводы на 100 г, г") }, singleLine = true, isError = attempted && carbs == null,
            supportingText = { Text(if (attempted && carbs == null) "Введите число от 0 до 100, например 12,5." else "Число от 0 до 100; запятая поддерживается.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(value = kcalText, onValueChange = { kcalText = it }, modifier = Modifier.fillMaxWidth().focusRequester(kcalFocus),
            label = { Text("Энергия на 100 г, ккал") }, singleLine = true, isError = attempted && kcal == null,
            supportingText = { Text(if (attempted && kcal == null) "Введите число от 0 до 1000." else "Используйте ккал, а не кДж.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Button(onClick = {
            attempted = true
            if (name.isNotBlank() && carbs != null && kcal != null) onSave(Food(
                id = "custom_${UUID.randomUUID()}", name = name.trim(), category = "Мои продукты", carbsPer100g = carbs,
                kcalPer100g = kcal, sourceName = "Введено вами по этикетке", custom = true,
            ))
            else when {
                name.isBlank() -> nameFocus.requestFocus()
                carbs == null -> carbsFocus.requestFocus()
                else -> kcalFocus.requestFocus()
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Сохранить продукт") }
    }
}

@Composable
private fun SourcesSheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    NutritionSheet("О справочнике", onDismiss) {
        Icon(Icons.Outlined.Restaurant, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(FoodCatalog.referenceNote, style = MaterialTheme.typography.bodyLarge)
        Text(FoodCatalog.carbohydrateNote, style = MaterialTheme.typography.bodyLarge)
        Text("База: общие продукты USDA FoodData Central / Standard Reference. Углеводы округлены до 0,1 г, энергия — до целых ккал. Это ориентир, а не анализ конкретной упаковки.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Рецепты — примеры сочетания продуктов. Они не учитывают ваши назначения, аллергии или индивидуальную реакцию на еду.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { runCatching { uriHandler.openUri("https://fdc.nal.usda.gov/") } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Открыть USDA FoodData Central")
        }
        TextButton(onClick = { runCatching { uriHandler.openUri("https://fdc.nal.usda.gov/data-documentation.html") } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Как устроены справочные данные")
        }
    }
}
