package io.github.lerchenflo.taptarget

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Registers this composable as the spotlight target for the tour step with [id].
 *
 * This is the **only** thing a call site needs to add — the controller is read
 * automatically from the surrounding [TapTargetHost]:
 *
 * ```kotlin
 * IconButton(
 *     modifier = Modifier.tapTarget("new_chat"),
 *     onClick = { … }
 * ) { Icon(Icons.Default.Add, null) }
 * ```
 *
 * While the composable is on screen the modifier:
 * - reports its bounds on every layout pass, so the spotlight follows it (and forgets them when
 *   the composable leaves the composition)
 * - scrolls it to the middle of the screen when its step starts, if it sits in a vertically
 *   scrollable container ([TourSettings.autoScroll])
 * - on pass-through steps ([TourStep.passTapToTarget]) notices a tap on the composable and goes to
 *   the next step; the composable itself still gets the tap
 *
 * The spotlight covers the size the modifier chain has at this point: placed before `padding`, the
 * padding is inside the spotlight; placed after it, only the inner content is.
 *
 * If there is no [TapTargetHost] above this composable the modifier does nothing,
 * so existing screens, previews and tests stay safe even outside a tour context.
 *
 * @param id  Id of the step that spotlights this composable, the same string as in
 *            [TourBuilder.tapStep] / [TourBuilder.highlightStep]. Use each id on only one
 *            composable at a time.
 */
fun Modifier.tapTarget(id: String): Modifier = this then TapTargetElement(id)

private data class TapTargetElement(val id: String) : ModifierNodeElement<TapTargetNode>() {
    override fun create(): TapTargetNode = TapTargetNode(id)

    override fun update(node: TapTargetNode) {
        node.updateId(id)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tapTarget"
        properties["id"] = id
    }
}

private class TapTargetNode(private var id: String) :
    Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode,
    GlobalPositionAwareModifierNode,
    PointerInputModifierNode {

    private var controller: TapTargetController? = null
    private var lastCoordinates: LayoutCoordinates? = null
    private var autoScrollJob: Job? = null

    // Tap detection for pass-through steps: the pointer that went down on this target, and where
    private var pressedPointer: PointerId? = null
    private var pressedPosition = Offset.Zero

    override fun onAttach() {
        bindController()
    }

    // Bounds are only valid while this composable is on screen. Without this the entry would
    // linger after the screen leaves composition, and the tour would spotlight (and gate taps on)
    // a stale rectangle where the element used to be.
    override fun onDetach() {
        controller?.unregister(id)
        controller = null
        lastCoordinates = null
        autoScrollJob = null
        pressedPointer = null
    }

    // Only watches (Final pass, nothing consumed), so the composable itself still gets the tap.
    // The overlay leaves the spotlight free only on pass-through steps; on other steps no event
    // reaches this node.
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Final) return
        val change = pointerEvent.changes.firstOrNull() ?: return
        when {
            change.changedToDownIgnoreConsumed() -> {
                pressedPointer = change.id
                pressedPosition = change.position
            }
            change.id != pressedPointer -> Unit
            change.changedToUpIgnoreConsumed() -> {
                pressedPointer = null
                val inside = change.position.x in 0f..bounds.width.toFloat() &&
                    change.position.y in 0f..bounds.height.toFloat()
                if (inside) controller?.onTargetTapped(id)
            }
            // Moved too far: a scroll or drag, not a tap
            (change.position - pressedPosition).getDistance() > currentValueOf(LocalViewConfiguration).touchSlop ->
                pressedPointer = null
        }
    }

    override fun onCancelPointerInput() {
        pressedPointer = null
    }

    // Called when the TapTargetHost above provides a different controller
    override fun onObservedReadsChanged() {
        bindController()
    }

    fun updateId(newId: String) {
        if (newId == id) return
        controller?.unregister(id)
        id = newId
        lastCoordinates?.let { register(it) }
        if (isAttached) startAutoScroll()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        lastCoordinates = coordinates
        register(coordinates)
    }

    private fun bindController() {
        var newController: TapTargetController? = null
        observeReads { newController = currentValueOf(LocalTapTargetController) }
        if (newController === controller) return

        controller?.unregister(id)
        controller = newController
        lastCoordinates?.let { register(it) }
        startAutoScroll()
    }

    private fun register(coordinates: LayoutCoordinates) {
        val controller = controller ?: return
        if (!coordinates.isAttached) return
        val pos = coordinates.positionInRoot()
        controller.register(
            id,
            Rect(
                left   = pos.x,
                top    = pos.y,
                right  = pos.x + coordinates.size.width,
                bottom = pos.y + coordinates.size.height,
            )
        )
    }

    // Auto-scrolls this target into the center 40% band of the screen once it becomes the
    // active step's target. Runs once per step change - not on every layout pass - so it
    // doesn't fight the scroll animation it just started.
    private fun startAutoScroll() {
        autoScrollJob?.cancel()
        val controller = controller ?: return
        autoScrollJob = coroutineScope.launch {
            // -1 while the tour is not running, so start() on step 0 also counts as a change
            snapshotFlow { if (controller.isActive) controller.currentIndex else -1 }
                .collectLatest {
                    if (controller.currentStep?.id == id && controller.tourSettings.autoScroll) {
                        try {
                            scrollIntoCenter(controller)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Auto-scroll is a nicety: if a parent cannot scroll, the step still shows
                        }
                    }
                }
        }
    }

    private suspend fun scrollIntoCenter(controller: TapTargetController) {
        withTimeoutOrNull(controller.tourSettings.targetTimeout) {
            while (lastCoordinates == null) delay(16.milliseconds)
        }
        val coords = lastCoordinates ?: return
        if (!coords.isAttached) return

        val rootSize = coords.findRootCoordinates().size
        val pos = coords.positionInRoot()
        val targetCenterY = pos.y + coords.size.height / 2f
        val bandTop = rootSize.height * 0.3f
        val bandBottom = rootSize.height * 0.7f
        if (targetCenterY in bandTop..bandBottom) return

        val w = coords.size.width.toFloat()
        val h = coords.size.height.toFloat()
        // Vertical span is padded to the root's height, centered on the target's own local
        // center — against Compose's default bring-into-view algorithm this resolves to
        // "scroll until the target's center reaches the viewport's center" on whichever
        // ancestor is vertically scrollable, i.e. it centers rather than just nudges into view.
        //
        // Horizontal span is left as the target's own natural bounds (0..w), NOT padded the
        // same way. A target inside a HorizontalPager (used for swipe navigation between
        // tabs) would otherwise look off-page to that pager, which then pages forward to
        // "bring it into view" — instantly swiping the user to the next tab. Only touch the
        // axis we actually mean to center on.
        bringIntoView {
            Rect(
                left   = 0f,
                top    = h / 2f - rootSize.height / 2f,
                right  = w,
                bottom = h / 2f + rootSize.height / 2f,
            )
        }
    }
}
