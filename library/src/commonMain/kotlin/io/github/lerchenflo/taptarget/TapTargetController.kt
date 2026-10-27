package io.github.lerchenflo.taptarget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

// ─────────────────────────────────────────────────────────────────
// Controller
// ─────────────────────────────────────────────────────────────────

/** Stores the screen-space bounding box of a registered target composable. */
data class TargetInfo(val bounds: Rect)

/**
 * Single source of truth for the active onboarding tour.
 *
 * Create exactly one instance with [rememberTapTargetController] at the root of your
 * composition, then pass it to [TapTargetHost]. Do **not** pass it around manually —
 * call sites only need [tapTarget].
 *
 * @param tour                The tour definition produced by [tapTargetTour].
 * @param onNavigateToRoute   Called when the current step lives on a different screen.
 *                            Implementation depends on the navigation library in use.
 * @param currentRoute        Returns the currently active route, used to decide
 *                            whether navigation is needed before a step.
 * @param isMatchingRoute     Decides if the active route already shows the step's route.
 *                            Defaults to comparing the classes (so data classes with
 *                            different arguments count as the same screen).
 * @param onFinished          Invoked when the tour completes (all steps shown) or
 *                            is skipped. Persist "onboarding_seen" here.
 * @param tourSettings        Behaviour and look, see [TourSettings]. Can be changed later.
 * @param onStepShown         A step became visible (its target was found). For analytics.
 * @param onSkipped           The tour was skipped on the step with this index (before [onFinished]).
 * @param onWrongTap          A tap next to the target on a step that needs an exact tap.
 *                            The position is in root coordinates.
 * @param onTargetMissing     A step's target did not appear within [TourSettings.targetTimeout].
 * @param onError             An exception thrown by one of the app's callbacks (or `beforeShow`).
 *                            The library catches it, so a broken callback never crashes the app;
 *                            the tour goes on (a step whose navigation failed is skipped).
 */
class TapTargetController(
    private val tour: TapTargetTour,
    private val onNavigateToRoute: suspend (Any) -> Unit = {},
    private val currentRoute: () -> Any? = { null },
    private val isMatchingRoute: (targetRoute: Any, activeRoute: Any) -> Boolean =
        { target, active -> target::class == active::class },
    private val onFinished: () -> Unit = {},
    tourSettings: TourSettings = TourSettings(),
    private val onStepShown: (index: Int, step: TourStep) -> Unit = { _, _ -> },
    private val onSkipped: (index: Int) -> Unit = {},
    private val onWrongTap: (index: Int, position: Offset) -> Unit = { _, _ -> },
    private val onTargetMissing: (index: Int, id: String) -> Unit = { _, _ -> },
    private val onError: (Throwable) -> Unit = {},
) {
    /** Behaviour and look of the tour. Changes apply at once, also while the tour runs. */
    var tourSettings by mutableStateOf(tourSettings)

    // Direction of the last move, so a missing target is skipped the way the user is going
    private var movingForward = true

    /** Runs app code: an exception goes to [onError] instead of crashing the app. */
    private inline fun <T> safely(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try { onError(e) } catch (_: Exception) { }
            fallback
        }

    // Targets registered by Modifier.tapTarget — keyed by step id.
    private val targets = mutableStateMapOf<String, TargetInfo>()

    /** Zero-based index of the step currently being spotlighted. */
    var currentIndex by mutableStateOf(0)
        private set

    /** Whether the tour is currently active. Defaults to false. */
    var active by mutableStateOf(false)
        private set

    /** The [TourStep] being spotlighted right now, or null when the tour is over. */
    val currentStep: TourStep? get() = if (isActive) tour.steps.getOrNull(currentIndex) else null

    /** The bounds of the currently spotlighted composable, or null if not yet laid out. */
    val currentTarget: TargetInfo? get() = currentStep?.id?.let { targets[it] }

    /** True while tour is active and there are still steps left to show. */
    val isActive: Boolean get() = active && currentIndex < tour.steps.size

    /** Number of steps in the tour. */
    val stepCount: Int get() = tour.steps.size

    /** True while a step shows that blocks the app (every step except free-roam steps). */
    val isBlocking: Boolean get() = currentStep?.isBlocking == true

    // ── Target registration ──────────────────────────────────────

    /** Called automatically by [tapTarget] on every layout pass. */
    fun register(id: String, bounds: Rect) {
        targets[id] = TargetInfo(bounds)
    }

    /** Called automatically by [tapTarget] when the composable leaves the composition. */
    fun unregister(id: String) {
        targets.remove(id)
    }

    /** Puts back the progress saved by [rememberTapTargetController] after the activity was recreated. */
    internal fun restoreState(index: Int, isActive: Boolean) {
        currentIndex = index.coerceIn(0, tour.steps.size)
        active = isActive
    }

    /** Resets the tour to the first step and activates it. */
    fun start() {
        movingForward = true
        currentIndex = 0
        active = true
    }

    /**
     * Advances to the next step, or finishes the tour if all steps have been shown.
     * Bound to the user's tap gesture on the overlay.
     */
    fun next() {
        movingForward = true
        if (currentIndex < tour.steps.size) currentIndex++
        if (currentIndex >= tour.steps.size) {
            active = false
            safely(Unit) { onFinished() }
        }
    }

    /**
     * Immediately ends the tour without showing the remaining steps.
     * Calls [onFinished] so persistence can happen in the same callback.
     */
    /** Goes back one step. Does nothing on the first step or when the tour does not run. */
    fun previous() {
        if (!isActive || currentIndex == 0) return
        movingForward = false
        currentIndex--
    }

    /** Jumps to the step at [index] (clamped to the tour) and starts the tour if it does not run. */
    fun goTo(index: Int) {
        if (tour.steps.isEmpty()) return
        val target = index.coerceIn(0, tour.steps.lastIndex)
        movingForward = target >= currentIndex
        currentIndex = target
        active = true
    }

    fun skip() {
        if (isActive) safely(Unit) { onSkipped(currentIndex) }
        currentIndex = tour.steps.size
        active = false
        safely(Unit) { onFinished() }
    }

    /**
     * Internal: called by [TapTargetOverlay] inside a `LaunchedEffect` to guarantee
     * the target composable is visible before the overlay draws it.
     *
     * 1. If the step declares a [TourStep.route] and it differs from [currentRoute], triggers navigation.
     * 2. Runs [TourStep.beforeShow].
     * 3. Polls until the target registers itself (max [TourSettings.targetTimeout]).
     *
     * @return false if the step's target did not show up in time.
     */
    internal suspend fun ensureCurrentStepVisible(): Boolean {
        val step = currentStep ?: return true

        val route = step.route
        val activeRoute = safely(null) { currentRoute() }
        if (route != null && (activeRoute == null || !safely(false) { isMatchingRoute(route, activeRoute) })) {
            safely(Unit) { onNavigateToRoute(route) }
        }
        step.beforeShow?.let { beforeShow -> safely(Unit) { beforeShow() } }

        val id = step.id ?: return true   // no target to wait for
        if (targets.containsKey(id)) return true

        withTimeoutOrNull(tourSettings.targetTimeout) {
            while (!targets.containsKey(id)) delay(16.milliseconds)
        }
        return targets.containsKey(id)
    }

    /**
     * Overlay: watches the route while a free-roam step shows and applies
     * [TourSettings.freeRoamLeaveBehavior] when the user leaves the step's screen.
     * Polls instead of observing, so it works with navigation state that is not snapshot state.
     */
    internal suspend fun watchFreeRoamLeave() {
        val step = currentStep ?: return
        val stepRoute = step.route ?: return
        if (step.isBlocking) return
        val index = currentIndex
        while (isActive && currentIndex == index) {
            delay(200.milliseconds)
            val behavior = tourSettings.freeRoamLeaveBehavior
            if (behavior == FreeRoamLeaveBehavior.Allow) continue
            val activeRoute = safely(null) { currentRoute() } ?: continue
            if (safely(true) { isMatchingRoute(stepRoute, activeRoute) }) continue
            when (behavior) {
                FreeRoamLeaveBehavior.ReturnToStepRoute -> safely(Unit) { onNavigateToRoute(stepRoute) }
                FreeRoamLeaveBehavior.NextStep -> { next(); return }
                FreeRoamLeaveBehavior.SkipTour -> { skip(); return }
                FreeRoamLeaveBehavior.Allow -> Unit
            }
        }
    }

    /** Overlay: the current step is on screen. */
    internal fun notifyStepShown() {
        currentStep?.let { step -> safely(Unit) { onStepShown(currentIndex, step) } }
    }

    /** Overlay: the current step's target never appeared. Skips it in the direction of the last move. */
    internal fun handleMissingTarget() {
        val id = currentStep?.id ?: return
        safely(Unit) { onTargetMissing(currentIndex, id) }
        if (!tourSettings.skipMissingTargets) return
        if (movingForward || currentIndex == 0) next() else previous()
    }

    /** Overlay: a tap next to the target on an exact-tap step. */
    internal fun reportWrongTap(position: Offset) {
        if (isActive) safely(Unit) { onWrongTap(currentIndex, position) }
    }

    /** Whether a tap on the current target also reaches the real composable. */
    internal fun passesTapToTarget(step: TourStep): Boolean =
        step.passTapToTarget ?: tourSettings.passTapToTarget

    internal fun spotlightShapeOf(step: TourStep): SpotlightShape =
        step.spotlightShape ?: tourSettings.spotlightShape

    /** [tapTarget]: the real composable with [id] was tapped. Advances on pass-through steps. */
    internal fun onTargetTapped(id: String) {
        val step = currentStep ?: return
        if (step.id == id && passesTapToTarget(step)) next()
    }
}

/**
 * Creates a [TapTargetController] and remembers it, including the tour progress (current step and
 * whether the tour runs) across activity recreation and process death on Android.
 *
 * The callbacks may capture changing values: the controller always calls the latest ones.
 * [tourSettings] is applied on every composition, so it can change while the tour runs.
 * [tour] is read once, when the controller is created.
 *
 * ```kotlin
 * val tourController = rememberTapTargetController(
 *     tour = onboardingTour,
 *     onNavigateToRoute = { route -> navigator.navigate(route as Route) },
 *     currentRoute = { backStack.lastOrNull() },
 *     onFinished = { viewModel.onAction(Action.OnboardingFinished) },
 * )
 * ```
 *
 * See [TapTargetController] for the parameters.
 */
@Composable
fun rememberTapTargetController(
    tour: TapTargetTour,
    onNavigateToRoute: suspend (Any) -> Unit = {},
    currentRoute: () -> Any? = { null },
    isMatchingRoute: (targetRoute: Any, activeRoute: Any) -> Boolean =
        { target, active -> target::class == active::class },
    onFinished: () -> Unit = {},
    tourSettings: TourSettings = TourSettings(),
    onStepShown: (index: Int, step: TourStep) -> Unit = { _, _ -> },
    onSkipped: (index: Int) -> Unit = {},
    onWrongTap: (index: Int, position: Offset) -> Unit = { _, _ -> },
    onTargetMissing: (index: Int, id: String) -> Unit = { _, _ -> },
    onError: (Throwable) -> Unit = {},
): TapTargetController {
    val latestOnNavigateToRoute by rememberUpdatedState(onNavigateToRoute)
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestIsMatchingRoute by rememberUpdatedState(isMatchingRoute)
    val latestOnFinished by rememberUpdatedState(onFinished)
    val latestOnStepShown by rememberUpdatedState(onStepShown)
    val latestOnSkipped by rememberUpdatedState(onSkipped)
    val latestOnWrongTap by rememberUpdatedState(onWrongTap)
    val latestOnTargetMissing by rememberUpdatedState(onTargetMissing)
    val latestOnError by rememberUpdatedState(onError)

    val createController = {
        TapTargetController(
            tour = tour,
            onNavigateToRoute = { route -> latestOnNavigateToRoute(route) },
            currentRoute = { latestCurrentRoute() },
            isMatchingRoute = { target, active -> latestIsMatchingRoute(target, active) },
            onFinished = { latestOnFinished() },
            tourSettings = tourSettings,
            onStepShown = { index, step -> latestOnStepShown(index, step) },
            onSkipped = { index -> latestOnSkipped(index) },
            onWrongTap = { index, position -> latestOnWrongTap(index, position) },
            onTargetMissing = { index, id -> latestOnTargetMissing(index, id) },
            onError = { error -> latestOnError(error) },
        )
    }

    val controller = rememberSaveable(
        saver = listSaver<TapTargetController, Any>(
            save = { controller -> listOf(controller.currentIndex, controller.active) },
            restore = { saved ->
                createController().apply {
                    // Safe casts: unexpected saved data starts the controller fresh instead of crashing
                    restoreState(
                        index = saved.getOrNull(0) as? Int ?: 0,
                        isActive = saved.getOrNull(1) as? Boolean ?: false,
                    )
                }
            },
        ),
        init = createController,
    )
    SideEffect { controller.tourSettings = tourSettings }
    return controller
}

/**
 * Provides the active [TapTargetController] to every composable in the subtree.
 * [TapTargetHost] sets it, read it to control the tour from a screen (e.g. `start()`).
 *
 * Defaults to `null` so that [tapTarget] is a no-op in previews, tests,
 * or screens not part of any tour.
 */
val LocalTapTargetController = staticCompositionLocalOf<TapTargetController?> { null }
