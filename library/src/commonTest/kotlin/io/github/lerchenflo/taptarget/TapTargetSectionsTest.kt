package io.github.lerchenflo.taptarget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/** dependsOn, notInFullTour, sections and the per-step target timeout. */
@OptIn(ExperimentalTestApi::class)
class TapTargetSectionsTest {

    /**
     * welcome, openChat, chatPolls (needs openChat), chatName (needs openChat), nickname (needs
     * chatName), rest; groupMembers only outside the full tour.
     */
    private val tour = tapTargetTour {
        infoStep { }
        section("chat") {
            tapStep(id = "openChat", targetTimeout = Duration.ZERO, tags = setOf("chat-list")) { }
            highlightStep(id = "chatPolls", dependsOn = "openChat", tags = setOf("chat")) { }
            tapStep(id = "chatName", dependsOn = "openChat", tags = setOf("chat")) { }
            highlightStep(id = "nickname", dependsOn = "chatName", tags = setOf("chat-details")) { }
        }
        tapStep(id = "rest", tags = setOf("main")) { }
        notInFullTour {
            highlightStep(id = "groupMembers", tags = setOf("group-details")) { }
        }
    }

    private class Recorder {
        val finished = mutableListOf<Set<String>>()
        val missing = mutableListOf<String>()
    }

    private fun controller(recorder: Recorder = Recorder()) = TapTargetController(
        tour = tour,
        onFinished = { tags -> recorder.finished += tags },
        onTargetMissing = { _, id -> recorder.missing += id },
    )

    private fun TapTargetController.showAndNext() {
        notifyStepShown()
        next()
    }

    @Test
    fun full_start_leaves_out_steps_outside_the_full_tour() {
        val controller = controller()
        controller.start()
        assertEquals(6, controller.stepCount)
        assertTrue("group-details" in tour.allTags)

        assertTrue(controller.start(setOf("group-details")))
        assertEquals("groupMembers", controller.currentStep?.id)
    }

    @Test
    fun steps_whose_dependency_was_passed_over_are_passed_over_too_in_a_chain() {
        val recorder = Recorder()
        val controller = controller(recorder)
        controller.start()
        controller.showAndNext()            // welcome
        controller.handleMissingTarget()    // openChat: no friend chat

        assertEquals("chatPolls", controller.currentStep?.id)
        assertTrue(controller.passOverDependentStep())
        assertTrue(controller.passOverDependentStep())   // chatName
        assertTrue(controller.passOverDependentStep())   // nickname, through chatName
        assertEquals("rest", controller.currentStep?.id)
        assertFalse(controller.passOverDependentStep())
        controller.showAndNext()

        // Only the step that really looked for its target reports it missing
        assertEquals(listOf("openChat"), recorder.missing)
        // The chat steps stay unseen, so they can auto-launch later
        assertEquals(listOf(setOf("main")), recorder.finished)
    }

    @Test
    fun dependent_steps_run_when_their_dependency_was_shown() {
        val controller = controller()
        controller.start()
        controller.showAndNext()   // welcome
        controller.showAndNext()   // openChat
        assertFalse(controller.passOverDependentStep())
        assertEquals("chatPolls", controller.currentStep?.id)
    }

    @Test
    fun a_dependency_outside_the_run_is_ignored() {
        val controller = controller()
        controller.start(setOf("chat-details"))
        assertFalse(controller.passOverDependentStep())
        assertEquals("nickname", controller.currentStep?.id)
    }

    @Test
    fun skipSection_jumps_to_the_first_step_after_the_section_and_counts_it_as_seen() {
        val recorder = Recorder()
        val controller = controller(recorder)
        controller.start()
        assertFalse(controller.canSkipSection)   // welcome is in no section
        controller.showAndNext()

        assertEquals("chat", controller.currentSection)
        assertTrue(controller.canSkipSection)
        controller.skipSection()
        assertEquals("rest", controller.currentStep?.id)
        assertFalse(controller.canSkipSection)

        controller.showAndNext()
        assertEquals(listOf(setOf("chat-list", "chat", "chat-details", "main")), recorder.finished)
    }

    @Test
    fun canSkipSection_is_false_on_the_last_step_of_a_section() {
        val controller = controller()
        controller.start(setOf("chat-details"))
        assertEquals("chat", controller.currentSection)
        assertFalse(controller.canSkipSection)
    }

    @Test
    fun a_zero_timeout_passes_a_missing_target_over_without_waiting() = runComposeUiTest {
        mainClock.autoAdvance = false
        val controller = TapTargetController(
            tour = tapTargetTour {
                tapStep(id = "missing", targetTimeout = Duration.ZERO) { }
                tapStep(id = "alsoMissing") { }
            },
        )
        setContent {
            TapTargetHost(controller = controller) { Box(Modifier.size(10.dp)) }
        }
        runOnIdle { controller.start() }
        repeat(3) { mainClock.advanceTimeByFrame() }
        // The first step gave up at once, the second still waits (default 500 ms without route)
        runOnIdle { assertEquals(1, controller.currentIndex) }
        mainClock.autoAdvance = true
        waitUntil(timeoutMillis = 5_000) { !controller.isActive }
        runOnIdle { assertNull(controller.currentStep) }
    }
}
