package com.morningwords.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.morningwords.domain.model.WordCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VocabularyLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val phrase = "butterflies in one's stomach"
    private val example = "With butterflies in my stomach, I breathed deeply. (P4)"
    private val card get() = WordCard(1, phrase, null, "phr.", "情绪紧张，心里发慌", "情绪紧张，心里发慌", example)

    private fun layout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun assertComfortableLines(result: TextLayoutResult) {
        assertFalse("The entire phrase must remain visible", result.hasVisualOverflow)
        val fontPixels = with(result.layoutInput.density) { result.layoutInput.style.fontSize.toPx() }
        for (line in 1 until result.lineCount) {
            assertTrue("Wrapped lines need breathing room", result.getLineBaseline(line) - result.getLineBaseline(line - 1) >= fontPixels * 1.25f)
            assertTrue("Line boxes must not overlap", result.getLineTop(line) >= result.getLineBottom(line - 1) - 1f)
        }
    }

    @Test fun longStudyPhraseHasComfortableCenteredLinesAndWorkingPronunciation() {
        var spoken = false
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF54715A), surface = Color(0xFFFFFDF9), onSurface = Color(0xFF292621), outline = Color(0xFFD1C8BA),
            )) {
                Box(Modifier.width(320.dp).height(480.dp)) {
                    StudyWordCard(card, true, false, true, { spoken = true }, Modifier.testTag("card"))
                }
            }
        }
        val result = layout(compose.onNodeWithText(phrase))
        assertTrue(result.lineCount >= 2)
        assertComfortableLines(result)
        for (line in 0 until result.lineCount) {
            assertEquals(result.size.width / 2f, (result.getLineLeft(line) + result.getLineRight(line)) / 2f, 2f)
        }
        compose.onNodeWithText(example).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("播放美音").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(spoken) }
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(folder, "long-phrase-card.png").outputStream().use {
            compose.onNodeWithTag("card").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun largeTextOnSmallCardCanScrollToFullExampleWithoutClippingPhrase() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                MaterialTheme {
                    Box(Modifier.width(260.dp).height(280.dp)) {
                        StudyWordCard(card, true, true, true, {})
                    }
                }
            }
        }
        val result = layout(compose.onNodeWithText(phrase))
        assertTrue(result.lineCount >= 3)
        assertComfortableLines(result)
        compose.onNodeWithText(example).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(phrase).performScrollTo().assertExists()
    }

    @Test fun listAndDetailHeadingsKeepFullPhraseAndGenerousLineSpacing() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(260.dp)) {
                    Surface {
                        VocabularyHeading(phrase, VocabularyHeadingSize.DETAIL, Modifier.testTag("detail"))
                    }
                    Box(Modifier.width(160.dp)) {
                        VocabularyHeading(phrase, VocabularyHeadingSize.LIST, Modifier.testTag("list"))
                    }
                }
            }
        }
        for (tag in listOf("detail", "list")) {
            val result = layout(compose.onNodeWithTag(tag))
            assertTrue(result.lineCount >= 2)
            assertEquals(phrase, result.layoutInput.text.text)
            assertComfortableLines(result)
        }
    }

    @Test fun shortWordsKeepTheirLargeReadableHeading() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    VocabularyHeading("maintain", VocabularyHeadingSize.STUDY, largeFont = true)
                }
            }
        }
        val result = layout(compose.onNodeWithText("maintain"))
        assertEquals(48f, result.layoutInput.style.fontSize.value)
        assertEquals(1, result.lineCount)
        assertComfortableLines(result)
    }
}
