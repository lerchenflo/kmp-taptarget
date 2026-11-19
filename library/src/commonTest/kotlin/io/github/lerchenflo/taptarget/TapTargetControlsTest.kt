package io.github.lerchenflo.taptarget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Placement of the skip controls and steps that explain them. */
@OptIn(ExperimentalTestApi::class)
class TapTargetControlsTest {

    @Test
    fun the_controls_move_to_a_corner_that_does_not_cover_the_target() = runComposeUiTest {
        // A tall target on the right whose center is in the upper half: the old rule put the
        // controls into the bottom right corner, on top of the target
        val controller = TapTargetController(
            tour = TapTargetTour(listOf(TourStep(id = "tall"))),
            tourSettings = TourSettings(bubble = BubbleStyle(showTapHint = false)),
        )
        setContent {
            TapTargetHost(
                controller = controller,
                skipButton = { onSkip -> Box(Modifier.size(48.dp).testTag("skip").clickable(onClick = onSkip)) },
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .width(120.dp)
                            .fillMaxHeight(0.95f)
                            .tapTarget("tall")
                    )
                }
            }
        }
        runOnIdle { controller.start() }
        waitUntil(timeoutMillis = 5_000) { controller.currentTarget != null }
        waitForIdle()

        val target = runOnIdle { controller.currentTarget!!.bounds }
        val skip = onNodeWithTag("skip").assertIsDisplayed().getBoundsInRoot()
        val skipLeftPx = with(density) { skip.left.toPx() }
        val skipRightPx = with(density) { skip.right.toPx() }
        assertTrue(skipRightPx <= target.left || skipLeftPx >= target.right, "skip control covers the target")
    }

    @Test
    fun a_step_on_a_control_shows_both_controls_and_their_taps_do_nothing() = runComposeUiTest {
        var sectionsSkipped = 0
        val controller = TapTargetController(
            tour = tapTargetTour {
                highlightStep(id = TourControls.SKIP_TOUR) { }
                highlightStep(id = TourControls.SKIP_SECTION) { }
                infoStep { }
            },
            onSkipped = { sectionsSkipped++ },
        )
        setContent {
            TapTargetHost(
                controller = controller,
                skipButton = { onSkip -> TourSkipIconButton("End tour", onSkip) },
                skipSectionButton = { onSkipSection -> TourSkipSectionIconButton("Next part", onSkipSection) },
            ) {
                Box(Modifier.fillMaxSize())
            }
        }
        runOnIdle { controller.start() }
        waitUntil(timeoutMillis = 5_000) { controller.currentTarget != null }

        // Both controls show, although no section runs
        onNodeWithContentDescription("Next part").assertIsDisplayed()
        onNodeWithContentDescription("End tour").assertIsDisplayed().performClick()
        runOnIdle {
            assertTrue(controller.isActive)
            assertEquals(0, controller.currentIndex)
            assertEquals(0, sectionsSkipped)
        }

        runOnIdle { controller.next() }
        waitUntil(timeoutMillis = 5_000) { controller.currentTarget != null }
        onNodeWithContentDescription("Next part").performClick()
        runOnIdle { assertEquals(1, controller.currentIndex) }

        // On a normal step the controls work again, and the section control hides outside a section
        runOnIdle { controller.next() }
        waitForIdle()
        onNodeWithContentDescription("Next part").assertDoesNotExist()
        onNodeWithContentDescription("End tour").performClick()
        runOnIdle {
            assertFalse(controller.isActive)
            assertEquals(1, sectionsSkipped)
        }
    }
}
