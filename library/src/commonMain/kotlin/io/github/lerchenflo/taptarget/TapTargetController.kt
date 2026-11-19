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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ─────────────────────────────────────────────────────────────────
// Controller
// ─────────────────────────────────────────────────────────────────

/**
 * Where a registered target composable is on screen.
 *
 * @property bounds  Bounding box of the target in root coordinates (px), from its last layout pass.
 *                   The spotlight is drawn around it.
 */
data class TargetInfo(val bounds: Rect)

/**
 * Single source of truth for the active onboarding tour: which step shows, whether the tour runs
 * and where the targets are on screen.
 *
 * Create exactly one instance with [rememberTapTargetController] at the root of your
 * composition, then pass it to [TapTargetHost]. Do **not** pass it around manually —
 * call sites only need [tapTarget], and a screen that controls the tour (e.g. a "restart tour"
 * button) reads it from [LocalTapTargetController].
 *
 * A tour runs in one of three ways:
 * - [start]: every step, e.g. from a "show tour" button.
 * - `start(tags)`: only the steps with a matching [TourStep.tags].
 * - Auto-launch: the steps whose tags are in [autoStartTags] start by themselves when their
 *   screen shows, see [autoStartTags].
 *
 * [onFinished] gets the tags the user has seen, so the app can store them and pass the rest as
 * [autoStartTags]: new or unlocked steps then show exactly once, on the screen they belong to.
 *
 * @param tour                The tour definition produced by [tapTargetTour].
 * @param onNavigateToRoute   Called with a step's [TourStep.route] when that step lives on a
 *                            different screen than [currentRoute]. Navigate there with your
 *                            navigation library (cast the route to your route type). It suspends:
 *                            the step waits until it returns, then waits for its target.
 *                            Default: does nothing (tours on one screen).
 * @param currentRoute        Returns the route that shows right now (e.g. `backStack.lastOrNull()`).
 *                            Read before each step, every 200 ms during a free-roam step and during
 *                            an auto-launched run, and to find the steps of the open screen for an
 *                            auto-launch. Null (the default) means "unknown": a step with a route
 *                            then always calls [onNavigateToRoute].
 * @param isMatchingRoute     Decides if the active route already shows the step's route, so no
 *                            navigation is needed. Also decides which steps belong to the open
 *                            screen for an auto-launch. Defaults to comparing the classes (so data
 *                            classes with different arguments count as the same screen). Pass
 *                            `{ target, active -> target == active }` to compare the arguments too.
 * @param onFinished          Called when a run of the tour ends: after its last step, when it is
 *                            skipped ([skip], the skip button, back or Escape), when an auto-launched
 *                            run ends because the screen changed, or at once when a start finds no
 *                            step to run. Gets the tags the user has now seen: a tag counts as seen
 *                            when every step of the tour that carries it was shown, or skipped by
 *                            the user, since the controller was created. A step passed over because
 *                            its target never appeared is not seen. Tags passed to `start(tags)`
 *                            that no step carries count as seen. Store the tags here. Not called
 *                            when a new start replaces a running tour.
 * @param tourSettings        Behaviour and look, see [TourSettings]. Can be changed later through
 *                            [TapTargetController.tourSettings].
 * @param onStepShown         A step became visible (its target was found), with its zero-based index
 *                            in the running steps. For analytics.
 * @param onSkipped           The tour was skipped on the step with this zero-based index (in the
 *                            running steps). Called before [onFinished].
 * @param onWrongTap          A tap next to the target on a step that needs an exact tap
 *                            ([TourStep.requireExactTap]), with the step index and the tap position
 *                            in root coordinates (px).
 * @param onTargetMissing     The target with this id did not appear within [TourSettings.targetTimeout]
 *                            (or [TourSettings.targetTimeoutWithoutRoute]) on the step with this
 *                            index (e.g. a typo in the id, or the element is not on the screen).
 *                            With [TourSettings.skipMissingTargets] the step is then skipped.
 * @param onError             An exception thrown by one of the app's callbacks (or `beforeShow`).
 *                            The library catches it, so a broken callback never crashes the app;
 *                            the tour goes on (a step whose navigation failed is skipped).
 * @param autoStartTags       Initial value of [TapTargetController.autoStartTags]. Empty (the
 *                            default): nothing starts by itself.
 */
class TapTargetController(
    private val tour: TapTargetTour,
    private val onNavigateToRoute: suspend (Any) -> Unit = {},
    private val currentRoute: () -> Any? = { null },
    private val isMatchingRoute: (targetRoute: Any, activeRoute: Any) -> Boolean =
        { target, active -> target::class == active::class },
    private val onFinished: (seenTags: Set<String>) -> Unit = {},
    tourSettings: TourSettings = TourSettings(),
    private val onStepShown: (index: Int, step: TourStep) -> Unit = { _, _ -> },
    private val onSkipped: (index: Int) -> Unit = {},
    private val onWrongTap: (index: Int, position: Offset) -> Unit = { _, _ -> },
    private val onTargetMissing: (index: Int, id: String) -> Unit = { _, _ -> },
    private val onError: (Throwable) -> Unit = {},
    autoStartTags: Set<String> = emptySet(),
) {
    /** Behaviour and look of the tour. Changes apply at once, also while the tour runs. */
    var tourSettings by mutableStateOf(tourSettings)

    /**
     * Tags whose steps start by themselves (auto-launch), usually [TapTargetTour.allTags] minus the
     * tags the app stored from [onFinished]. Empty: nothing starts by itself.
     *
     * When no tour runs and a [tapTarget] appears whose step carries one of these tags, the
     * controller waits until the target stops moving (e.g. the screen's enter animation) and then
     * runs the steps of the open screen that carry one of these tags, in tour order:
     * - steps with a [TourStep.route] that matches [currentRoute] (via `isMatchingRoute`), and
     * - steps without a route whose target is on screen.
     *
     * Steps with a target only join when the target is on screen, so the run never waits for a
     * missing element. Steps without a target (info, free-roam) need a route. A step that was
     * shown since the controller was created does not launch again. The run never navigates, and
     * it ends when the screen changes; its steps not shown yet launch the next time their screen
     * shows. Steps without tags never launch by themselves.
     */
    var autoStartTags by mutableStateOf(autoStartTags)

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

    // Tour indices of the steps that run: all, the tagged ones of start(tags), or an auto-launch
    private var runIndices: List<Int> = tour.steps.indices.toList()

    // The steps that run, in order. State, so the overlay sees a new run at the same index.
    private var steps by mutableStateOf(tour.steps)

    // Tags onFinished may report for this run: all tags, the tags of start(tags), or the pending
    // tags an auto-launched run carries
    private var candidateTags: Set<String> = tour.allTags

    // True while the run was started by auto-launch: it ends when the screen changes
    private var autoRun by mutableStateOf(false)

    // Screen of the auto-launched run, null when unknown (e.g. after process death)
    private var autoRunRoute: Any? = null

    // Tour indices shown, or skipped by the user, since the controller was created
    private val seenSteps = mutableSetOf<Int>()

    // Tour indices of this run passed over because their target never appeared
    private val missedSteps = mutableSetOf<Int>()

    /**
     * Zero-based index of the step that shows (or shows next), counted in the running steps
     * (only the matching ones after `start(tags)` or an auto-launch). Equals [stepCount] after
     * the tour finished or was skipped.
     */
    var currentIndex by mutableStateOf(0)
        private set

    /**
     * True from a start / [goTo] until the tour finishes or is skipped. Defaults to false.
     * Prefer [isActive], which also checks that a step is left to show.
     */
    var active by mutableStateOf(false)
        private set

    /** The [TourStep] that shows right now, or null when the tour does not run. */
    val currentStep: TourStep? get() = if (isActive) steps.getOrNull(currentIndex) else null

    /**
     * Where the current step's target is, or null when the step has no target or the target is
     * not laid out yet (e.g. while the navigation to its screen runs).
     */
    val currentTarget: TargetInfo? get() = currentStep?.id?.let { targets[it] }

    /** True while the tour runs and there are still steps left to show. */
    val isActive: Boolean get() = active && currentIndex < steps.size

    /**
     * Number of steps that run: every step of the tour, or only the matching ones after
     * `start(tags)` or an auto-launch. The progress text ("2 / 5") uses it.
     */
    val stepCount: Int get() = steps.size

    /**
     * True while a step shows that blocks the app (every step except free-roam steps).
     * False when the tour does not run.
     */
    val isBlocking: Boolean get() = currentStep?.isBlocking == true

    // ── Target registration ──────────────────────────────────────

    /**
     * Stores where the target with [id] is, so a step with that id can spotlight it.
     * Called automatically by [tapTarget] on every layout pass; call it yourself only for a
     * target that cannot use the modifier.
     *
     * @param id      The id a step refers to ([TourStep.id]).
     * @param bounds  Bounds of the target in root coordinates (px).
     */
    fun register(id: String, bounds: Rect) {
        targets[id] = TargetInfo(bounds)
    }

    /**
     * Forgets the target with [id], so no step spotlights a stale position.
     * Called automatically by [tapTarget] when the composable leaves the composition.
     */
    fun unregister(id: String) {
        targets.remove(id)
    }

    // ── Starting and moving ──────────────────────────────────────

    /**
     * Starts the tour at the first step and runs every step, e.g. from a "show tour" button.
     * A running or finished tour starts again from the beginning (without calling [onFinished]
     * for the replaced run). A tour without steps calls [onFinished] at once.
     */
    fun start() {
        begin(tour.steps.indices.toList(), tour.allTags, auto = false)
    }

    /**
     * Starts a filtered tour: runs only the steps with at least one of [tags] in their
     * [TourStep.tags], in tour order ([TapTargetTour.stepsWithTags]). Steps without tags do not
     * run. The tour only navigates to the routes of the steps that run. A running or finished
     * tour starts again from the first matching step (without calling [onFinished] for the
     * replaced run).
     *
     * When no step matches, the tour does not show and [onFinished] is called at once (with
     * [tags], since no step of them is left to see).
     *
     * @param tags  The tags to run. Compared as plain strings, the library has no version logic.
     * @return True when at least one step matched and the tour runs, false when nothing matched.
     */
    fun start(tags: Collection<String>): Boolean {
        val wanted = tags.toSet()
        val indices = tour.steps.indices.filter { tour.steps[it].matchesAnyTag(wanted) }
        return begin(indices, wanted, auto = false)
    }

    private fun begin(indices: List<Int>, candidates: Set<String>, auto: Boolean): Boolean {
        useRun(indices)
        candidateTags = candidates
        autoRun = auto
        autoRunRoute = if (auto) safely(null) { currentRoute() } else null
        missedSteps.clear()
        movingForward = true
        currentIndex = 0
        if (indices.isEmpty()) {
            active = false
            finish()
            return false
        }
        active = true
        return true
    }

    private fun useRun(indices: List<Int>) {
        runIndices = indices
        steps = indices.map { tour.steps[it] }
    }

    /**
     * Advances to the next step. After the last step the tour finishes: [isActive] turns false
     * and [onFinished] is called.
     * The overlay calls it on a tap that continues the step; call it yourself e.g. from a custom
     * button inside a step's content.
     */
    fun next() {
        movingForward = true
        if (currentIndex < steps.size) currentIndex++
        if (currentIndex >= steps.size) finish()
    }

    /**
     * Goes back one step. Does nothing on the first step or when the tour does not run.
     * System back calls it by default ([TourSettings.backBehavior]).
     */
    fun previous() {
        if (!isActive || currentIndex == 0) return
        movingForward = false
        currentIndex--
    }

    /**
     * Jumps to the step at [index] and starts the tour if it does not run.
     * Does nothing when the last run has no steps.
     *
     * @param index  Zero-based step index, counted in the steps of the last start (every step, or
     *               only the matching ones after `start(tags)` or an auto-launch). Values outside
     *               are clamped to the first or last step.
     */
    fun goTo(index: Int) {
        if (steps.isEmpty()) return
        val target = index.coerceIn(0, steps.lastIndex)
        movingForward = target >= currentIndex
        currentIndex = target
        active = true
    }

    /**
     * Immediately ends the tour without showing the remaining steps. The remaining steps count as
     * seen (the user chose to skip them).
     * Calls [onSkipped] (when the tour runs) and then [onFinished], so persistence can happen in
     * the same callback. The skip button, Escape and [TourBackBehavior.SkipTour] call it.
     */
    fun skip() {
        if (isActive) {
            safely(Unit) { onSkipped(currentIndex) }
            for (position in currentIndex until runIndices.size) {
                val index = runIndices[position]
                if (index !in missedSteps) seenSteps += index
            }
        }
        currentIndex = steps.size
        finish()
    }

    private fun finish() {
        active = false
        autoRun = false
        val seen = candidateTags.filterTo(LinkedHashSet()) { tag -> isTagSeen(tag) }
        safely(Unit) { onFinished(seen) }
    }

    private fun isTagSeen(tag: String): Boolean =
        tour.steps.indices.all { index -> tag !in tour.steps[index].tags || index in seenSteps }

    // ── Auto-launch ──────────────────────────────────────────────

    /**
     * Overlay: watches the registered targets and starts an auto-launch when a target of a
     * pending step appears and no tour runs. Waits until the targets stop moving first.
     */
    internal suspend fun watchAutoLaunch() {
        snapshotFlow { if (isActive) emptySet() else autoLaunchTriggers() }
            .collectLatest { ids ->
                if (ids.isEmpty()) return@collectLatest
                awaitStableTargets(ids)
                startAutoLaunch()
            }
    }

    /** Ids of registered targets whose step would auto-launch now. */
    internal fun autoLaunchTriggers(): Set<String> {
        val pending = autoStartTags
        if (pending.isEmpty()) return emptySet()
        val ids = LinkedHashSet<String>()
        tour.steps.forEachIndexed { index, step ->
            val id = step.id ?: return@forEachIndexed
            if (id in targets && index !in seenSteps && step.matchesAnyTag(pending)) ids += id
        }
        return ids
    }

    /**
     * Starts the pending steps of the open screen, see [autoStartTags].
     * @return True when a run started.
     */
    internal fun startAutoLaunch(): Boolean {
        if (isActive) return false
        val pending = autoStartTags
        if (pending.isEmpty()) return false
        val route = safely(null) { currentRoute() }
        val indices = tour.steps.indices.filter { index ->
            val step = tour.steps[index]
            index !in seenSteps && step.matchesAnyTag(pending) && isOnOpenScreen(step, route)
        }
        // A run needs at least one target on screen; info and free-roam steps only join it
        if (indices.none { tour.steps[it].id != null }) return false
        val candidates = indices.flatMapTo(LinkedHashSet()) { tour.steps[it].tags }.filterTo(LinkedHashSet()) { it in pending }
        return begin(indices, candidates, auto = true)
    }

    private fun isOnOpenScreen(step: TourStep, route: Any?): Boolean {
        val stepRoute = step.route
        val routeMatches = stepRoute == null || route == null || safely(false) { isMatchingRoute(stepRoute, route) }
        return when {
            !routeMatches -> false
            step.id != null -> step.id in targets
            // Info / free-roam steps: only with a known route of this screen
            else -> stepRoute != null && route != null
        }
    }

    /** Waits until the targets kept their bounds for 250 ms (at most 2 s). */
    private suspend fun awaitStableTargets(ids: Set<String>) {
        withTimeoutOrNull(2.seconds) {
            var last = ids.map { targets[it]?.bounds }
            var stableFor = Duration.ZERO
            while (stableFor < 250.milliseconds) {
                delay(50.milliseconds)
                val now = ids.map { targets[it]?.bounds }
                if (now == last) stableFor += 50.milliseconds else { stableFor = Duration.ZERO; last = now }
            }
        }
    }

    /**
     * Overlay: ends an auto-launched run when the screen changes. Polls instead of observing, so
     * it works with navigation state that is not snapshot state.
     */
    internal suspend fun watchAutoRunRoute() {
        snapshotFlow { autoRun && isActive }.collectLatest { running ->
            if (!running) return@collectLatest
            while (true) {
                delay(200.milliseconds)
                val now = safely(null) { currentRoute() } ?: continue
                val runRoute = autoRunRoute
                if (runRoute == null) {
                    autoRunRoute = now
                    continue
                }
                if (!safely(true) { isMatchingRoute(runRoute, now) }) {
                    endAutoRun()
                    return@collectLatest
                }
            }
        }
    }

    /** Ends an auto-launched run early; steps not shown yet stay pending. */
    internal fun endAutoRun() {
        if (!autoRun || !isActive) return
        currentIndex = steps.size
        finish()
    }

    // ── Saved state ──────────────────────────────────────────────

    /** Everything [restoreState] needs, as Bundle-friendly values. */
    internal fun saveState(): List<Any> = listOf(
        currentIndex,
        active,
        ArrayList(runIndices),
        ArrayList(candidateTags),
        autoRun,
        ArrayList(seenSteps),
        ArrayList(missedSteps),
    )

    /** Puts back the progress saved by [rememberTapTargetController] after the activity was recreated. */
    internal fun restoreState(
        index: Int,
        isActive: Boolean,
        runIndices: List<Int>? = null,
        candidateTags: Set<String>? = null,
        autoRun: Boolean = false,
        seenSteps: Collection<Int> = emptyList(),
        missedSteps: Collection<Int> = emptyList(),
    ) {
        val validRun = runIndices?.takeIf { run -> run.all { it in tour.steps.indices } }
        useRun(validRun ?: tour.steps.indices.toList())
        this.candidateTags = if (validRun != null) candidateTags ?: tour.allTags else tour.allTags
        this.autoRun = validRun != null && autoRun
        autoRunRoute = null
        this.seenSteps.addAll(seenSteps.filter { it in tour.steps.indices })
        this.missedSteps.addAll(missedSteps.filter { it in tour.steps.indices })
        currentIndex = index.coerceIn(0, steps.size)
        active = isActive
    }

    internal fun restoreState(saved: List<Any?>) {
        // Safe casts: unexpected saved data starts the controller fresh instead of crashing
        fun ints(position: Int) = (saved.getOrNull(position) as? List<*>)?.filterIsInstance<Int>()
        restoreState(
            index = saved.getOrNull(0) as? Int ?: 0,
            isActive = saved.getOrNull(1) as? Boolean ?: false,
            runIndices = ints(2),
            candidateTags = (saved.getOrNull(3) as? List<*>)?.filterIsInstance<String>()?.toSet(),
            autoRun = saved.getOrNull(4) as? Boolean ?: false,
            seenSteps = ints(5).orEmpty(),
            missedSteps = ints(6).orEmpty(),
        )
    }

    // ── Overlay hooks ────────────────────────────────────────────

    /**
     * Internal: called by [TapTargetOverlay] inside a `LaunchedEffect` to guarantee
     * the target composable is visible before the overlay draws it.
     *
     * 1. If the step declares a [TourStep.route] and it differs from [currentRoute], triggers navigation.
     * 2. Runs [TourStep.beforeShow].
     * 3. Polls until the target registers itself (max [TourSettings.targetTimeout], or
     *    [TourSettings.targetTimeoutWithoutRoute] for a step without route).
     *
     * @return false if the step's target did not show up in time.
     */
    internal suspend fun ensureCurrentStepVisible(): Boolean {
        val step = currentStep ?: return true

        val route = step.route
        val activeRoute = safely(null) { currentRoute() }
        if (autoRun) {
            // An auto-launched run never navigates: a new screen ends it (e.g. a pass-through tap
            // opened another screen before the route watcher noticed)
            val runRoute = autoRunRoute
            if (runRoute != null && activeRoute != null && !safely(true) { isMatchingRoute(runRoute, activeRoute) }) {
                endAutoRun()
                return true
            }
        } else if (route != null && (activeRoute == null || !safely(false) { isMatchingRoute(route, activeRoute) })) {
            safely(Unit) { onNavigateToRoute(route) }
        }
        step.beforeShow?.let { beforeShow -> safely(Unit) { beforeShow() } }

        val id = step.id ?: return true   // no target to wait for
        if (targets.containsKey(id)) return true

        // Without a route the target should already be on the open screen, so give up sooner
        val timeout = if (route == null) tourSettings.targetTimeoutWithoutRoute else tourSettings.targetTimeout
        withTimeoutOrNull(timeout) {
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
        val step = currentStep ?: return
        runIndices.getOrNull(currentIndex)?.let { seenSteps += it }
        safely(Unit) { onStepShown(currentIndex, step) }
    }

    /** Overlay: the current step's target never appeared. Skips it in the direction of the last move. */
    internal fun handleMissingTarget() {
        val id = currentStep?.id ?: return
        runIndices.getOrNull(currentIndex)?.let { if (it !in seenSteps) missedSteps += it }
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
        !step.highlightOnly && (step.passTapToTarget ?: tourSettings.passTapToTarget)

    internal fun spotlightShapeOf(step: TourStep): SpotlightShape =
        step.spotlightShape ?: tourSettings.spotlightShape

    /** [tapTarget]: the real composable with [id] was tapped. Advances on pass-through steps. */
    internal fun onTargetTapped(id: String) {
        val step = currentStep ?: return
        if (step.id == id && passesTapToTarget(step)) next()
    }
}

/**
 * Creates a [TapTargetController] and remembers it, including the tour progress (current step,
 * whether the tour runs, which steps run and which were seen) across activity recreation and
 * process death on Android.
 * Call it once at the root of the app and pass the result to [TapTargetHost].
 *
 * The callbacks may capture changing values: the controller always calls the latest ones.
 * [tourSettings] and [autoStartTags] are applied on every composition, so they can change while
 * the tour runs. [tour] is read once, when the controller is created.
 *
 * ```kotlin
 * val seenTags by viewModel.seenTourTags.collectAsState()   // stored by the app
 * val tourController = rememberTapTargetController(
 *     tour = onboardingTour,
 *     onNavigateToRoute = { route -> navigator.navigate(route as Route) },
 *     currentRoute = { backStack.lastOrNull() },
 *     autoStartTags = onboardingTour.allTags - seenTags,
 *     onFinished = { tags -> viewModel.onAction(Action.TourTagsSeen(tags)) },
 * )
 * ```
 *
 * @param tour                The tour definition produced by [tapTargetTour].
 * @param onNavigateToRoute   Called with a step's [TourStep.route] when that step lives on a
 *                            different screen than [currentRoute]. Navigate there with your
 *                            navigation library (cast the route to your route type). It suspends:
 *                            the step waits until it returns, then waits for its target.
 *                            Default: does nothing (tours on one screen).
 * @param currentRoute        Returns the route that shows right now (e.g. `backStack.lastOrNull()`).
 *                            Read before each step, every 200 ms during a free-roam step and during
 *                            an auto-launched run, and to find the steps of the open screen for an
 *                            auto-launch. Null (the default) means "unknown": a step with a route
 *                            then always calls [onNavigateToRoute].
 * @param isMatchingRoute     Decides if the active route already shows the step's route, so no
 *                            navigation is needed. Also decides which steps belong to the open
 *                            screen for an auto-launch. Defaults to comparing the classes (so data
 *                            classes with different arguments count as the same screen). Pass
 *                            `{ target, active -> target == active }` to compare the arguments too.
 * @param onFinished          Called when a run of the tour ends, with the tags the user has now
 *                            seen. Store them. See [TapTargetController] for the details.
 * @param tourSettings        Behaviour and look, see [TourSettings]. Applied on every composition,
 *                            so a new value takes effect while the tour runs.
 * @param onStepShown         A step became visible (its target was found), with its zero-based index
 *                            in the running steps. For analytics.
 * @param onSkipped           The tour was skipped on the step with this zero-based index (in the
 *                            running steps). Called before [onFinished].
 * @param onWrongTap          A tap next to the target on a step that needs an exact tap
 *                            ([TourStep.requireExactTap]), with the step index and the tap position
 *                            in root coordinates (px).
 * @param onTargetMissing     The target with this id did not appear in time on the step with this
 *                            index (e.g. a typo in the id, or the element is not on the screen).
 *                            With [TourSettings.skipMissingTargets] the step is then skipped.
 * @param onError             An exception thrown by one of the app's callbacks (or `beforeShow`).
 *                            The library catches it, so a broken callback never crashes the app;
 *                            the tour goes on (a step whose navigation failed is skipped).
 * @param autoStartTags       Tags whose steps start by themselves when their screen shows, usually
 *                            [TapTargetTour.allTags] minus the stored seen tags. See
 *                            [TapTargetController.autoStartTags]. Applied on every composition.
 *                            Empty (the default): nothing starts by itself. Pass an empty set until
 *                            the stored tags are loaded.
 * @return The remembered controller. Nothing runs until [TapTargetController.start] is called or
 *         an auto-launch starts.
 */
@Composable
fun rememberTapTargetController(
    tour: TapTargetTour,
    onNavigateToRoute: suspend (Any) -> Unit = {},
    currentRoute: () -> Any? = { null },
    isMatchingRoute: (targetRoute: Any, activeRoute: Any) -> Boolean =
        { target, active -> target::class == active::class },
    onFinished: (seenTags: Set<String>) -> Unit = {},
    tourSettings: TourSettings = TourSettings(),
    onStepShown: (index: Int, step: TourStep) -> Unit = { _, _ -> },
    onSkipped: (index: Int) -> Unit = {},
    onWrongTap: (index: Int, position: Offset) -> Unit = { _, _ -> },
    onTargetMissing: (index: Int, id: String) -> Unit = { _, _ -> },
    onError: (Throwable) -> Unit = {},
    autoStartTags: Set<String> = emptySet(),
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
            onFinished = { tags -> latestOnFinished(tags) },
            tourSettings = tourSettings,
            onStepShown = { index, step -> latestOnStepShown(index, step) },
            onSkipped = { index -> latestOnSkipped(index) },
            onWrongTap = { index, position -> latestOnWrongTap(index, position) },
            onTargetMissing = { index, id -> latestOnTargetMissing(index, id) },
            onError = { error -> latestOnError(error) },
            autoStartTags = autoStartTags,
        )
    }

    val controller = rememberSaveable(
        saver = listSaver<TapTargetController, Any>(
            save = { controller -> controller.saveState() },
            restore = { saved -> createController().apply { restoreState(saved) } },
        ),
        init = createController,
    )
    SideEffect {
        controller.tourSettings = tourSettings
        controller.autoStartTags = autoStartTags
    }
    return controller
}

/**
 * Provides the active [TapTargetController] to every composable in the subtree.
 * [TapTargetHost] sets it, read it to control the tour from a screen:
 *
 * ```kotlin
 * val tourController = LocalTapTargetController.current
 * TextButton(onClick = { tourController?.start() }) { Text("Show tour again") }
 * ```
 *
 * Defaults to `null` so that [tapTarget] is a no-op in previews, tests,
 * or screens not part of any tour.
 */
val LocalTapTargetController = staticCompositionLocalOf<TapTargetController?> { null }
