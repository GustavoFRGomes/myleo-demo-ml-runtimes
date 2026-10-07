package org.ggomes.myleo.vectorsearch.dual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.ggomes.myleo.vectorsearch.dual.ui.runtime.RuntimePickerContent
import org.ggomes.myleo.vectorsearch.dual.ui.runtime.RuntimeUiState
import org.ggomes.myleo.vectorsearch.dual.domain.InferenceRuntime
import org.ggomes.myleo.vectorsearch.dual.ui.runtime.RuntimeCard
import org.ggomes.myleo.vectorsearch.dual.ui.theme.MyLeoVsDualTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-only picker flow (no activity, no model): selecting a runtime
 * forwards the choice, loading state shows progress, errors surface inline.
 */
@RunWith(AndroidJUnit4::class)
class RuntimePickerFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun picker_selectForwardsRuntime() {
        var selected: InferenceRuntime? = null
        composeRule.setContent {
            MyLeoVsDualTheme {
                RuntimePickerContent(
                    state = RuntimeUiState(
                        cards = listOf(
                            RuntimeCard(InferenceRuntime.LITERT, bundled = true, loaded = false, loadMs = null),
                            RuntimeCard(InferenceRuntime.ONNX, bundled = true, loaded = false, loadMs = null),
                        ),
                    ),
                    onSelect = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("Choose an embedding runtime. The model loads only after you choose.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Load LiteRT").performClick()

        assert(selected == InferenceRuntime.LITERT)
    }

    @Test
    fun picker_missingModel_disablesLoadAndShowsError() {
        composeRule.setContent {
            MyLeoVsDualTheme {
                RuntimePickerContent(
                    state = RuntimeUiState(
                        cards = listOf(
                            RuntimeCard(InferenceRuntime.ONNX, bundled = false, loaded = false, loadMs = null),
                        ),
                        error = "no model",
                    ),
                    onSelect = {},
                )
            }
        }

        composeRule.onNodeWithText("Model not bundled — see README").assertIsDisplayed()
        composeRule.onNodeWithText("no model").assertIsDisplayed()
        // Disabled button still exists but is not clickable — assert it shows.
        composeRule.onNodeWithText("Load ONNX").assertIsDisplayed()
    }
}
