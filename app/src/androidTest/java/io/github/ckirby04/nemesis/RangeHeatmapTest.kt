package io.github.ckirby04.nemesis

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ckirby04.nemesis.engine.Cell
import io.github.ckirby04.nemesis.ui.components.MarkdownText
import io.github.ckirby04.nemesis.ui.components.RangeHeatmap
import io.github.ckirby04.nemesis.ui.components.evScaleBound
import io.github.ckirby04.nemesis.ui.theme.NemesisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RangeHeatmapTest {
    @get:Rule val compose = createComposeRule()

    /** Tap the center of grid cell (row, col) and return what the heatmap reported. */
    private fun tapCell(row: Int, col: Int): String? {
        var picked: String? = null
        compose.setContent {
            NemesisTheme {
                var selected by remember { mutableStateOf<String?>(null) }
                RangeHeatmap(
                    cells = mapOf("AA" to Cell(30.0, 1.0, 100), "72o" to Cell(-8.0, 1.0, 100)),
                    selected = selected,
                    onSelect = { selected = it; picked = it },
                    modifier = Modifier.width(325.dp),
                )
            }
        }
        compose.onNodeWithContentDescription("13 by 13", substring = true).performTouchInput {
            val cell = width / 13f
            click(Offset((col + 0.5f) * cell, (row + 0.5f) * cell))
        }
        compose.waitForIdle()
        return picked
    }

    @Test fun tapTopLeft_isAces() = assertEquals("AA", tapCell(0, 0))
    @Test fun tapAboveDiagonal_isSuited() = assertEquals("AKs", tapCell(0, 1))
    @Test fun tapBelowDiagonal_isOffsuit() = assertEquals("AKo", tapCell(1, 0))
    @Test fun tapBottomRight_isDeuces() = assertEquals("22", tapCell(12, 12))
    @Test fun tapSevenDeuce() = assertEquals("72o", tapCell(12, 7))

    @Test fun scaleBound_ignoresOutliers() {
        val cells = (1..100).map { Cell(it / 10.0, 1.0, 100) } + Cell(500.0, 1.0, 100)
        assertEquals(9.6, evScaleBound(cells), 0.11)
    }

    @Test fun markdown_rendersHeadingsBulletsAndInlineBold() {
        compose.setContent {
            NemesisTheme { MarkdownText("# Title\n## Section\n- **bold** point\n---\nplain *italic* text") }
        }
        compose.onNodeWithText("Title").assertExists()
        compose.onNodeWithText("Section").assertExists()
        compose.onNodeWithText("bold point").assertExists()   // markers stripped, text kept
        compose.onNodeWithText("plain italic text").assertExists()
    }
}
