package io.github.lerchenflo.taptarget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TapTargetHostTest {

    @Test
    fun a_wrong_tap_is_reported_and_does_not_advance() = runComposeUiTest {
        var wrongTaps = 0
        val controller = TapTargetController(
            tour = tapTargetTour {
                tapStep(id = "target") { }
                infoStep { }
            },
            onWrongTap = { _, _ -> wrongTaps++ },
        )
        setContent {
            TapTargetHost(controller = controller) {
                Box(Modifier.padding(16.dp).size(48.dp).testTag("target").tapTarget("target"))
            }
        }
        runOnIdle { controller.start() }

        onRoot().performTouchInput { click(center) }
        runOnIdle {
            assertEquals(1, wrongTaps)
            assertEquals(0, controller.currentIndex)
        }

        onNodeWithTag("target").performTouchInput { click() }
        runOnIdle { assertEquals(1, controller.currentIndex) }
    }

    @Test
    fun a_pass_through_tap_reaches_the_target_and_advances() = runComposeUiTest {
        var clicked = false
        val controller = TapTargetController(
            tour = tapTargetTour {
                tapStep(id = "target", passTapToTarget = true) { }
                infoStep { }
            }
        )
        setContent {
            TapTargetHost(controller = controller) {
                Box(
                    Modifier
                        .padding(16.dp)
                        .size(48.dp)
                        .testTag("target")
                        .tapTarget("target")
                        .clickable { clicked = true }
                )
            }
        }
        runOnIdle { controller.start() }

        onNodeWithTag("target").performTouchInput { click() }
        runOnIdle {
            assertTrue(clicked)
            assertEquals(1, controller.currentIndex)
        }
    }

    @Test
    fun a_target_unregisters_when_it_leaves_the_composition() = runComposeUiTest {
        var showTarget by mutableStateOf(true)
        val controller = TapTargetController(
            tour = tapTargetTour { tapStep(id = "target") { } },
            tourSettings = TourSettings(skipMissingTargets = false),
        )
        setContent {
            TapTargetHost(controller = controller) {
                if (showTarget) Box(Modifier.size(48.dp).tapTarget("target"))
            }
        }
        runOnIdle { controller.start() }
        runOnIdle { assertNotNull(controller.currentTarget) }

        showTarget = false
        runOnIdle { assertNull(controller.currentTarget) }
    }
}
