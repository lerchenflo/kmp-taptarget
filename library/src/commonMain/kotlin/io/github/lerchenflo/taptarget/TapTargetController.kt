package io.github.lerchenflo.taptarget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

// ─────────────────────────────────────────────────────────────────
// Controller
// ─────────────────────────────────────────────────────────────────

/** Stores the screen-space bounding box of a registered target composable. */
data class TargetInfo(val bounds: Rect)

data class TourSettings(
    /** Padding between the target bounds and the spotlight hole. */
    val iconPadding: Dp = 4.dp,
    val cornerRadius: Dp = 16.dp,
    /** Skip a step whose target never shows up (e.g. a button hidden by state) instead of
     *  leaving the tour stuck on a step without overlay. */
    val skipMissingTargets: Boolean = true,
)

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
 */
class TapTargetController(
    private val tour: TapTargetTour,
    private val onNavigateToRoute: suspend (Any) -> Unit = {},
    private val currentRoute: () -> Any? = { null },
    private val isMatchingRoute: (targetRoute: Any, activeRoute: Any) -> Boolean =
        { target, active -> target::class == active::class },
    private val onFinished: () -> Unit = {},
    val tourSettings: TourSettings = TourSettings(),
) {
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
        currentIndex = 0
        active = true
    }

    /**
     * Advances to the next step, or finishes the tour if all steps have been shown.
     * Bound to the user's tap gesture on the overlay.
     */
    fun next() {
        if (currentIndex < tour.steps.size) currentIndex++
        if (currentIndex >= tour.steps.size) {
            active = false
            onFinished()
        }
    }

    /**
     * Immediately ends the tour without showing the remaining steps.
     * Calls [onFinished] so persistence can happen in the same callback.
     */
    fun skip() {
        currentIndex = tour.steps.size
        active = false
        onFinished()
    }

    /**
     * Internal: called by [TapTargetOverlay] inside a `LaunchedEffect` to guarantee
     * the target composable is visible before the overlay draws it.
     *
     * 1. If the step declares a [TourStep.route] and it differs from [currentRoute], triggers navigation.
     * 2. Polls until the target registers itself (max [timeoutMs] ms).
     *
     * @return false if the step's target did not show up in time.
     */
    internal suspend fun ensureCurrentStepVisible(timeoutMs: Long = 2000): Boolean {
        val step = currentStep ?: return true

        val route = step.route
        val activeRoute = currentRoute()
        if (route != null && (activeRoute == null || !isMatchingRoute(route, activeRoute))) {
            onNavigateToRoute(route)
        }

        val id = step.id ?: return true   // no target to wait for
        if (targets.containsKey(id)) return true

        withTimeoutOrNull(timeoutMs.milliseconds) {
            while (!targets.containsKey(id)) delay(16.milliseconds)
        }
        return targets.containsKey(id)
    }
}

/**
 * Creates a [TapTargetController] and remembers it, including the tour progress (current step and
 * whether the tour runs) across activity recreation and process death on Android.
 *
 * The callbacks may capture changing values: the controller always calls the latest ones.
 * [tour] and [tourSettings] are read once, when the controller is created.
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
): TapTargetController {
    val latestOnNavigateToRoute by rememberUpdatedState(onNavigateToRoute)
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestIsMatchingRoute by rememberUpdatedState(isMatchingRoute)
    val latestOnFinished by rememberUpdatedState(onFinished)

    val createController = {
        TapTargetController(
            tour = tour,
            onNavigateToRoute = { route -> latestOnNavigateToRoute(route) },
            currentRoute = { latestCurrentRoute() },
            isMatchingRoute = { target, active -> latestIsMatchingRoute(target, active) },
            onFinished = { latestOnFinished() },
            tourSettings = tourSettings,
        )
    }

    return rememberSaveable(
        saver = listSaver<TapTargetController, Any>(
            save = { controller -> listOf(controller.currentIndex, controller.active) },
            restore = { saved ->
                createController().apply {
                    restoreState(index = saved[0] as Int, isActive = saved[1] as Boolean)
                }
            },
        ),
        init = createController,
    )
}

/**
 * Provides the active [TapTargetController] to every composable in the subtree.
 * [TapTargetHost] sets it, read it to control the tour from a screen (e.g. `start()`).
 *
 * Defaults to `null` so that [tapTarget] is a no-op in previews, tests,
 * or screens not part of any tour.
 */
val LocalTapTargetController = staticCompositionLocalOf<TapTargetController?> { null }
