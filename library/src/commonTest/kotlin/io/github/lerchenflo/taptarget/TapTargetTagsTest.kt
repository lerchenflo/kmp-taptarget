package io.github.lerchenflo.taptarget

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TapTargetTagsTest {

    private data object Home
    private data class Chat(val chatId: Int)

    private val bounds = Rect(0f, 0f, 10f, 10f)

    /** welcome (no tags), home (main), chatA + chatB (chat), chatNew (chat, chat-2), friends (chat-friends). */
    private val tour = tapTargetTour {
        infoStep { }
        tapStep(id = "home", route = Home, tags = setOf("main")) { }
        tapStep(id = "chatA", route = Chat(1), tags = setOf("chat")) { }
        tapStep(id = "chatB", route = Chat(1), tags = setOf("chat")) { }
        tapStep(id = "chatNew", route = Chat(1), tags = setOf("chat", "chat-2")) { }
        tapStep(id = "friends", tags = setOf("chat-friends")) { }
    }

    private class Recorder {
        val finished = mutableListOf<Set<String>>()
    }

    private fun controller(
        recorder: Recorder = Recorder(),
        route: () -> Any? = { null },
        autoStartTags: Set<String> = emptySet(),
    ) = TapTargetController(
        tour = tour,
        currentRoute = route,
        onFinished = { tags -> recorder.finished += tags },
        autoStartTags = autoStartTags,
    )

    /** Shows the current step and moves on, like a tap of the user. */
    private fun TapTargetController.showAndNext() {
        notifyStepShown()
        next()
    }

    private fun TapTargetController.runningIds(): List<String?> {
        val ids = mutableListOf<String?>()
        repeat(stepCount) { index ->
            goTo(index)
            ids += currentStep?.id
        }
        return ids
    }

    @Test
    fun allTags_lists_every_tag_in_tour_order() {
        assertEquals(listOf("main", "chat", "chat-2", "chat-friends"), tour.allTags.toList())
    }

    @Test
    fun start_with_tags_runs_only_steps_with_any_matching_tag() {
        val controller = controller()
        assertTrue(controller.start(listOf("chat-2", "main")))
        assertEquals(listOf("home", "chatNew"), controller.runningIds())
    }

    @Test
    fun start_with_tags_without_match_does_not_run_and_reports_the_tags_as_seen() {
        val recorder = Recorder()
        val controller = controller(recorder)
        assertFalse(controller.start(setOf("unknown")))
        assertFalse(controller.isActive)
        assertEquals(listOf(setOf("unknown")), recorder.finished)
    }

    @Test
    fun full_start_runs_every_step_and_reports_every_tag_when_all_were_shown() {
        val recorder = Recorder()
        val controller = controller(recorder)
        controller.start()
        assertEquals(6, controller.stepCount)
        repeat(6) { controller.showAndNext() }
        assertEquals(listOf(tour.allTags), recorder.finished)
    }

    @Test
    fun a_step_passed_over_for_a_missing_target_keeps_its_tag_unseen() {
        val recorder = Recorder()
        val controller = controller(recorder)
        controller.start()
        repeat(5) { controller.showAndNext() }
        controller.handleMissingTarget()   // "friends": UI not unlocked yet
        assertFalse(controller.isActive)
        assertEquals(listOf(setOf("main", "chat", "chat-2")), recorder.finished)
    }

    @Test
    fun skip_counts_the_remaining_steps_as_seen() {
        val recorder = Recorder()
        val controller = controller(recorder)
        controller.start()
        controller.showAndNext()
        controller.skip()
        assertEquals(listOf(tour.allTags), recorder.finished)
    }

    @Test
    fun a_tag_is_only_seen_when_every_step_with_it_was_seen() {
        val recorder = Recorder()
        val controller = controller(recorder)
        controller.start(setOf("chat-2"))   // only chatNew, which also carries "chat"
        controller.showAndNext()
        assertEquals(listOf(setOf("chat-2")), recorder.finished)
    }

    @Test
    fun auto_launch_runs_the_pending_steps_of_the_open_screen() {
        val recorder = Recorder()
        val controller = controller(recorder, route = { Chat(7) }, autoStartTags = setOf("main", "chat", "chat-2"))
        controller.register("chatA", bounds)
        controller.register("chatNew", bounds)
        controller.register("home", bounds)   // e.g. a bottom bar item, but its step belongs to Home

        assertEquals(setOf("home", "chatA", "chatNew"), controller.autoLaunchTriggers())
        assertTrue(controller.startAutoLaunch())
        // chatB is not on screen, so it does not join; home belongs to another screen
        assertEquals(listOf("chatA", "chatNew"), controller.runningIds())

        controller.goTo(0)
        repeat(2) { controller.showAndNext() }
        // "chat" is not seen yet: chatB was never shown
        assertEquals(listOf(setOf("chat-2")), recorder.finished)
    }

    @Test
    fun auto_launch_does_not_repeat_steps_shown_before() {
        val controller = controller(route = { Chat(1) }, autoStartTags = setOf("chat"))
        controller.register("chatA", bounds)
        assertTrue(controller.startAutoLaunch())
        controller.showAndNext()

        assertEquals(emptySet(), controller.autoLaunchTriggers())
        assertFalse(controller.startAutoLaunch())
    }

    @Test
    fun auto_launch_starts_a_step_without_route_once_its_target_appears() {
        val controller = controller(route = { Home }, autoStartTags = setOf("chat-friends"))
        assertFalse(controller.startAutoLaunch())

        controller.register("friends", bounds)   // the first friend was added
        assertTrue(controller.startAutoLaunch())
        assertEquals("friends", controller.currentStep?.id)
    }

    @Test
    fun nothing_launches_without_autoStartTags_or_while_a_tour_runs() {
        val controller = controller(route = { Chat(1) })
        controller.register("chatA", bounds)
        assertEquals(emptySet(), controller.autoLaunchTriggers())
        assertFalse(controller.startAutoLaunch())

        controller.autoStartTags = setOf("chat")
        controller.start(setOf("main"))
        assertFalse(controller.startAutoLaunch())
        assertEquals("home", controller.currentStep?.id)
    }

    @Test
    fun an_auto_run_that_ends_early_reports_only_what_was_shown() {
        val recorder = Recorder()
        val controller = controller(recorder, route = { Chat(1) }, autoStartTags = setOf("chat", "chat-2"))
        listOf("chatA", "chatB", "chatNew").forEach { controller.register(it, bounds) }
        assertTrue(controller.startAutoLaunch())
        controller.showAndNext()   // chatA
        controller.endAutoRun()    // the screen changed

        assertFalse(controller.isActive)
        assertEquals(listOf(emptySet()), recorder.finished)
        // chatB and chatNew stay pending for the next visit
        assertEquals(setOf("chatB", "chatNew"), controller.autoLaunchTriggers())
    }

    @Test
    fun a_new_start_over_an_auto_run_does_not_report_the_replaced_run() {
        val recorder = Recorder()
        val controller = controller(recorder, route = { Chat(1) }, autoStartTags = setOf("chat"))
        controller.register("chatA", bounds)
        assertTrue(controller.startAutoLaunch())
        controller.start()
        assertTrue(recorder.finished.isEmpty())
        assertNull(controller.currentStep?.id)   // welcome info step of the full tour
        assertEquals(6, controller.stepCount)
    }

    @Test
    fun saved_state_keeps_the_running_steps_and_what_was_seen() {
        val controller = controller(route = { Chat(1) }, autoStartTags = setOf("chat"))
        listOf("chatA", "chatB").forEach { controller.register(it, bounds) }
        assertTrue(controller.startAutoLaunch())
        controller.showAndNext()

        val recorder = Recorder()
        val restored = controller(recorder, route = { Chat(1) }, autoStartTags = setOf("chat"))
        restored.restoreState(controller.saveState())
        assertEquals("chatB", restored.currentStep?.id)
        assertEquals(2, restored.stepCount)

        restored.showAndNext()
        // chatNew (also "chat") was never shown, so "chat" is still not seen
        assertEquals(listOf(emptySet()), recorder.finished)
    }
}
