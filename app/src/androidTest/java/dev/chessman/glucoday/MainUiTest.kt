package dev.chessman.glucoday

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chessman.glucoday.data.AppRepository
import dev.chessman.glucoday.domain.GlucoseConversions
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator-only smoke tests. They never clear the application database or edit a profile.
 * Only records carrying this test's unique marker/id are removed during cleanup.
 * Run on a disposable emulator; hardware phones intentionally skip the test bodies.
 */
@RunWith(AndroidJUnit4::class)
class MainUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var emulatorConfirmed = false
    private var glucoseMarker: String? = null
    private var createdWeightId: String? = null
    private val repository: AppRepository
        get() = (compose.activity.application as GlucoApplication).repository

    @Before fun requireEmulatorAndLoadedDiary() {
        // Standard Android Virtual Devices identify their virtual hardware explicitly.
        // A generic/custom-ROM fingerprint alone must not authorize writes on a phone.
        val emulator = Build.HARDWARE in setOf("ranchu", "goldfish")
        assumeTrue("UI smoke tests create synthetic records and must run only on a disposable emulator", emulator)
        emulatorConfirmed = true
        compose.waitUntil(15_000) { repository.snapshot.value.loaded }
        selectTab("Сегодня")
    }

    @After fun removeOnlyOwnSyntheticRecords() {
        if (!emulatorConfirmed) return
        runBlocking {
            glucoseMarker?.let { marker ->
                repository.snapshot.value.glucose.filter { it.note == marker }
                    .forEach { repository.deleteGlucose(it.id) }
            }
            createdWeightId?.let { repository.deleteWeight(it) }
        }
    }

    @Test fun todayQuickEntryAcceptsCommaAndShowsSavedRecordInDiary() {
        if (repository.snapshot.value.glucose.isEmpty()) {
            compose.onNodeWithText("Начнём с одного числа").assertIsDisplayed()
        }
        val marker = "UI_SMOKE_${UUID.randomUUID()}"
        glucoseMarker = marker
        val unit = repository.snapshot.value.profile.glucoseUnit
        compose.onNode(hasText("Добавить сахар") and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction() and hasText("Глюкоза,", substring = true)).performTextInput("5,7")
        compose.onNode(hasSetTextAction() and hasText("Заметка — необязательно"))
            .performScrollTo().performTextInput(marker)
        saveEditor()
        compose.waitUntil(10_000) { repository.snapshot.value.glucose.any { it.note == marker } }
        val saved = repository.snapshot.value.glucose.single { it.note == marker }
        assertEquals(GlucoseConversions.toMmol(5.7, unit), saved.valueMmol, 0.0)

        selectTab("Дневник")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(marker, substring = true))
        compose.onNode(hasText(marker, substring = true)).assertIsDisplayed()
    }

    @Test fun profileTabIsReachableAndReturnsToToday() {
        selectTab("Профиль")
        compose.onNode(hasText("Профиль") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Сегодня") and isSelectable()).assertIsNotSelected()
        compose.onNodeWithText("Всё под вас").assertIsDisplayed()
        compose.onNode(hasText("Изменить профиль") and hasClickAction()).performClick()
        compose.onNodeWithText("Личные настройки").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Как к вам обращаться")).assertIsDisplayed()
        compose.onNode(hasText("Отмена") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        selectTab("Сегодня")
        compose.onNode(hasText("Сегодня") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Добавить сахар") and hasClickAction()).assertIsDisplayed()
    }

    @Test fun weightEntryAcceptsCommaAndAppearsInDiary() {
        val previousIds = repository.snapshot.value.weights.mapTo(hashSetOf()) { it.id }
        val fractional = (UUID.randomUUID().leastSignificantBits.ushr(1) % 900_000L) + 100_000L
        val raw = "73,$fractional"
        val expected = raw.replace(',', '.').toDouble()
        selectTab("Шаги")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Записать вес"))
        compose.onNode(hasText("Записать вес") and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction() and hasText("Вес, кг")).performTextInput(raw)
        saveEditor()
        compose.waitUntil(10_000) {
            repository.snapshot.value.weights.any { it.id !in previousIds && it.kilograms == expected }
        }
        val saved = repository.snapshot.value.weights.single { it.id !in previousIds && it.kilograms == expected }
        createdWeightId = saved.id
        assertEquals(expected, saved.kilograms, 0.0)

        selectTab("Дневник")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Запись веса"))
        compose.onAllNodesWithText("Запись веса")[0].assertIsDisplayed()
    }

    private fun selectTab(label: String) {
        compose.onNode(hasText(label) and isSelectable()).performClick()
    }

    private fun saveEditor() {
        compose.onNode(hasText("Сохранить") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }
}
