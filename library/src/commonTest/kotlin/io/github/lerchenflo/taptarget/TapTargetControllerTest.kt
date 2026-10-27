package io.github.lerchenflo.taptarget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TapTargetControllerTest {

    private val threeInfoSteps = tapTargetTour {
        infoStep { }
        infoStep { }
        infoStep { }
    }

    @Test
    fun previous_goes_back_and_stops_at_the_first_step() {
        val controller = TapTargetController(tour = threeInfoSteps)
        controller.start()
        controller.next()
        controller.previous()
        assertEquals(0, controller.currentIndex)

        controller.previous()
        assertEquals(0, controller.currentIndex)
        assertTrue(controller.isActive)
    }

    @Test
    fun goTo_clamps_the_index_and_starts_the_tour() {
        val controller = TapTargetController(tour = threeInfoSteps)
        controller.goTo(10)
        assertEquals(2, controller.currentIndex)
        assertTrue(controller.isActive)
    }

    @Test
    fun restoreState_clamps_an_index_from_a_longer_tour() {
        val controller = TapTargetController(tour = threeInfoSteps)
        controller.restoreState(index = 7, isActive = true)
        assertEquals(3, controller.currentIndex)
        assertFalse(controller.isActive)
    }

    @Test
    fun a_missing_target_is_skipped_in_the_direction_of_the_last_move() {
        val controller = TapTargetController(
            tour = tapTargetTour {
                tapStep(id = "a") { }
                tapStep(id = "missing") { }
                tapStep(id = "c") { }
            }
        )
        controller.start()
        controller.next()
        controller.handleMissingTarget()
        assertEquals(2, controller.currentIndex)

        controller.previous()
        controller.handleMissingTarget()
        assertEquals(0, controller.currentIndex)
    }

    @Test
    fun skip_reports_the_step_it_was_skipped_on() {
        var skippedOn = -1
        var finished = false
        val controller = TapTargetController(
            tour = threeInfoSteps,
            onFinished = { finished = true },
            onSkipped = { index -> skippedOn = index },
        )
        controller.start()
        controller.next()
        controller.skip()
        assertEquals(1, skippedOn)
        assertTrue(finished)
        assertFalse(controller.isActive)
    }
}
