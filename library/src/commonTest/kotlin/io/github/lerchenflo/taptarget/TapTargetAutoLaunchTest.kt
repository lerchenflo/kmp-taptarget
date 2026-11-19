package io.github.lerchenflo.taptarget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class TapTargetAutoLaunchTest {

    private data object Home
    private data object Chat

    @Test
    fun a_pending_target_that_appears_launches_its_steps() = runComposeUiTest {
        var unlocked by mutableStateOf(false)
        val controller = TapTargetController(
            tour = tapTargetTour {
                tapStep(id = "other") { }
                tapStep(id = "friends", tags = setOf("friends")) { }
            },
            autoStartTags = setOf("friends"),
        )
        setContent {
            TapTargetHost(controller = controller) {
                Box(Modifier.padding(16.dp).size(48.dp).tapTarget("other"))
                if (unlocked) Box(Modifier.padding(80.dp).size(48.dp).tapTarget("friends"))
            }
        }
        mainClock.advanceTimeBy(3_000)
        runOnIdle { assertFalse(controller.isActive) }

        unlocked = true
        waitUntil(timeoutMillis = 5_000) { controller.isActive }
        runOnIdle {
            assertEquals("friends", controller.currentStep?.id)
            assertEquals(1, controller.stepCount)
        }
    }

    @Test
    fun an_auto_run_ends_when_the_screen_changes() = runComposeUiTest {
        var route: Any by mutableStateOf(Chat)
        val finished = mutableListOf<Set<String>>()
        val controller = TapTargetController(
            tour = tapTargetTour {
                tapStep(id = "a", route = Chat, tags = setOf("chat")) { }
                tapStep(id = "b", route = Chat, tags = setOf("chat")) { }
            },
            currentRoute = { route },
            onFinished = { tags -> finished += tags },
            autoStartTags = setOf("chat"),
        )
        setContent {
            TapTargetHost(controller = controller) {
                if (route == Chat) {
                    Box(Modifier.padding(16.dp).size(48.dp).tapTarget("a"))
                    Box(Modifier.padding(80.dp).size(48.dp).tapTarget("b"))
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { controller.isActive }

        route = Home
        waitUntil(timeoutMillis = 5_000) { !controller.isActive }
        runOnIdle {
            // Nothing of "chat" was seen completely, so it stays pending
            assertEquals(listOf(emptySet()), finished)
        }
    }
}
