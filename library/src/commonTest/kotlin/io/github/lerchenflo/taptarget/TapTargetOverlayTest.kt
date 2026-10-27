package io.github.lerchenflo.taptarget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TapTargetOverlayTest {

    @Test
    fun a_tap_on_the_bubble_advances_a_tap_anywhere_step() = runComposeUiTest {
        val controller = TapTargetController(
            tour = tapTargetTour {
                tapStep(id = "target", requireExactTap = false) { Text("Bubble") }
                infoStep { Text("Next") }
            }
        )
        setContent {
            CompositionLocalProvider(LocalTapTargetController provides controller) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(16.dp).size(48.dp).tapTarget("target"))
                    TapTargetOverlay(controller)
                }
            }
        }
        runOnIdle { controller.start() }

        onNodeWithText("Bubble").performClick()

        runOnIdle { assertEquals(1, controller.currentIndex) }
    }
}
