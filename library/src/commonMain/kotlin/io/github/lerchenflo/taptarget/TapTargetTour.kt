package io.github.lerchenflo.taptarget

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

// ─────────────────────────────────────────────────────────────────
// Tour DSL
// ─────────────────────────────────────────────────────────────────

/** Where the hint bar of a free-roam step ([TourBuilder.freeRoamStep]) sits on the screen. */
enum class FreeRoamBarPosition {
    /** Full-width banner at the top, below the status bar. */
    Top,

    /** Compact card in the middle of the screen. */
    Center,

    /** Full-width banner at the bottom, above the navigation bar. */
    Bottom,
}

/**
 * Content of the info bubble of a step. Runs inside a [androidx.compose.foundation.layout.Column]
 * with a small vertical spacing, so any composables (icons, rows, images...) can be placed here.
 * Use [TourTitle] and [TourDescription] for the default text styling.
 */
typealias TourStepContent = @Composable ColumnScope.() -> Unit

/** Light enough that the screen behind stays readable; the spotlight outline carries the focus. */
val DefaultTourBackgroundColor: Color = Color.Black.copy(alpha = 0.65f)

/**
 * Describes a single step in an onboarding tour. Usually created by the [TourBuilder] functions
 * ([TourBuilder.tapStep], [TourBuilder.highlightStep], [TourBuilder.infoStep],
 * [TourBuilder.freeRoamStep]) instead of this constructor.
 *
 * @param id                  Id of the target to spotlight; must match the id passed to [tapTarget].
 *                            Null for steps without a spotlight target (info / free-roam steps).
 * @param route               Optional route of the screen the step lives on. When the active route
 *                            does not match it, the controller calls `onNavigateToRoute` with it
 *                            before the step is displayed, so that the target composable is in the
 *                            composition. Null: the step shows on whatever screen is open.
 * @param backgroundColor     Scrim color. Unspecified: [TourSettings.scrimColor].
 * @param freeRoamPosition    Non-null marks this a free-roam step: shows a non-blocking hint bar at
 *                            this position instead of a spotlight or blocking card. Everything else
 *                            on screen stays fully interactive; only the bar's Continue button
 *                            advances the tour.
 * @param continueButtonText  Label of the free-roam Continue button or of the "Got it" button of a
 *                            highlight step. Null: [TourStrings.continueButton] / [TourStrings.gotIt].
 * @param requireExactTap     True: the user must tap exactly on the highlighted target to advance;
 *                            taps next to it give [WrongTapFeedback]. False: a tap anywhere on the
 *                            overlay advances — useful for steps that just show a button without
 *                            requiring the user to press it. Only used by spotlight steps.
 * @param passTapToTarget     Overrides [TourSettings.passTapToTarget] for this step. Null: use the setting.
 * @param spotlightShape      Overrides [TourSettings.spotlightShape] for this step. Null: use the setting.
 * @param beforeShow          Runs after the navigation to [route] and before the step waits for its
 *                            target, e.g. to scroll a lazy list to an item that is not composed yet.
 *                            Runs again each time the step shows (also after going back to it).
 * @param highlightOnly       The step only shows the target: calm dashed outline, lightly dimmed
 *                            cutout, a "Got it" button. A tap on the target does nothing, a tap
 *                            anywhere else continues. See [TourBuilder.highlightStep].
 * @param tags                Free strings that pick this step for `start(tags)` and for auto-launch
 *                            ([TapTargetController.autoStartTags]), e.g. "chat" or "chat-3.0.21".
 *                            The library gives them no meaning. Use one tag per screen. Empty (the
 *                            default): the step only runs in a full [TapTargetController.start].
 * @param dependsOn           Id of an earlier tap or highlight step this step needs, e.g. the step
 *                            that opens the screen of this one. When that step is part of the run
 *                            and was passed over (its target never appeared, or it was passed over
 *                            for its own [dependsOn]), this step is passed over at once: no
 *                            navigation, no waiting, and it stays unseen, so it can auto-launch
 *                            later on its own screen. Chains work. Ignored when that step is not
 *                            part of the run (`start(tags)`, auto-launch) or was shown.
 * @param targetTimeout       How long this step waits for its target. [Duration.ZERO]: no waiting,
 *                            the target must already be on screen. Null (the default):
 *                            [TourSettings.targetTimeout], or [TourSettings.targetTimeoutWithoutRoute]
 *                            for a step without route.
 * @param inFullTour          False: a full [TapTargetController.start] leaves the step out; it only
 *                            runs through `start(tags)` and auto-launch. See [TourBuilder.notInFullTour].
 * @param section             Name of the section the step belongs to ([TourBuilder.section]), or null.
 *                            Consecutive steps with the same name form one section, which the user
 *                            can jump over with [TapTargetController.skipSection].
 * @param content             Content of the info bubble, see [TourStepContent]. Null: the bubble
 *                            shows only the tap hint (or nothing).
 */
class TourStep(
    val id: String?,
    val route: Any? = null,
    val backgroundColor: Color = Color.Unspecified,
    val freeRoamPosition: FreeRoamBarPosition? = null,
    val continueButtonText: (@Composable () -> String)? = null,
    val requireExactTap: Boolean = true,
    val passTapToTarget: Boolean? = null,
    val spotlightShape: SpotlightShape? = null,
    val beforeShow: (suspend () -> Unit)? = null,
    val highlightOnly: Boolean = false,
    val tags: Set<String> = emptySet(),
    val dependsOn: String? = null,
    val targetTimeout: Duration? = null,
    val inFullTour: Boolean = true,
    val section: String? = null,
    val content: TourStepContent? = null,
)

/** True when at least one of the step's [TourStep.tags] is in [wanted]. */
internal fun TourStep.matchesAnyTag(wanted: Set<String>): Boolean = tags.any { it in wanted }

/** Whether a step expects a tap on the highlighted target or a tap anywhere on screen. */
internal enum class TapHint { EXACT, ANYWHERE }

/** Derives which tap hint to show, or null for free-roam steps that use a Continue button instead. */
internal val TourStep.tapHint: TapHint?
    get() = when {
        freeRoamPosition != null -> null
        highlightOnly -> null   // shows a "Got it" button instead of a hint
        id == null -> TapHint.ANYWHERE
        requireExactTap -> TapHint.EXACT
        else -> TapHint.ANYWHERE
    }

/** Free-roam steps let the user use the app, every other step blocks it. */
internal val TourStep.isBlocking: Boolean get() = freeRoamPosition == null

/**
 * Immutable tour description produced by [tapTargetTour]. Pass it to [rememberTapTargetController].
 *
 * @property steps  The steps in the order they show.
 */
class TapTargetTour(val steps: List<TourStep>) {
    /**
     * Every tag used by a step, in tour order. The app's pending tags for
     * [TapTargetController.autoStartTags] are usually `allTags - seenTags`.
     */
    val allTags: Set<String> = steps.flatMapTo(LinkedHashSet()) { it.tags }

    /** For each step, the index of the closest earlier step its [TourStep.dependsOn] names, or null. */
    internal val dependencyIndex: List<Int?> = steps.mapIndexed { index, step ->
        step.dependsOn?.let { id -> (index - 1 downTo 0).firstOrNull { steps[it].id == id } }
    }

    /**
     * The steps `start(tags)` ([TapTargetController.start]) would run: every step with at least
     * one of [tags] in its [TourStep.tags], in tour order. Steps without tags are never part of
     * it. Use it e.g. to check for new steps before asking the user to see them.
     *
     * @param tags  The tags to look for. Compared as plain strings, no version logic.
     * @return The matching steps, empty when none matches.
     */
    fun stepsWithTags(tags: Collection<String>): List<TourStep> {
        val wanted = tags.toSet()
        return steps.filter { step -> step.matchesAnyTag(wanted) }
    }
}

/**
 * Builder populated inside the [tapTargetTour] lambda. Steps show in the order they are added.
 *
 * Step types:
 * - [tapStep]: spotlights a target and waits for a tap on it
 * - [highlightStep]: points at a target only to inform, "Got it" continues
 * - [infoStep]: centered card without spotlight, a tap anywhere continues
 * - [freeRoamStep]: the app stays usable, a small bar with a Continue button stays on screen
 *
 * Every step type has two variants:
 * - a [StringResource] variant with `title` / `description` for plain text steps
 * - a slot variant with a trailing [TourStepContent] lambda for custom content (icons etc.)
 */
class TourBuilder {
    /** The steps added so far, in order. Add a custom [TourStep] here for full control. */
    val steps = mutableListOf<TourStep>()

    // False inside notInFullTour { }
    private var inFullTour = true

    // Name of the enclosing section { } block
    private var currentSection: String? = null

    /**
     * Adds the steps of [builder] at this position as one section named [name]. While a step of a
     * section shows, the user can jump to the first step after the section
     * ([TapTargetController.skipSection], the `skipSectionButton` slot of [TapTargetHost]); the
     * steps jumped over count as seen. Steps outside any section are not skippable this way.
     *
     * ```kotlin
     * section("map") {
     *     tapStep("map_tab", route = Route.Map) { ... }
     *     highlightStep("map_filter") { ... }
     * }
     * ```
     *
     * @param name     Name of the section ([TourStep.section]). Two sections in a row with the same
     *                 name count as one.
     * @param builder  Adds the steps, like the [tapTargetTour] lambda. A nested section replaces the
     *                 outer one for its steps.
     */
    fun section(name: String, builder: TourBuilder.() -> Unit) {
        val outer = currentSection
        currentSection = name
        try {
            builder()
        } finally {
            currentSection = outer
        }
    }

    /**
     * Adds the steps of [builder] at this position, but a full [TapTargetController.start] leaves
     * them out ([TourStep.inFullTour] = false). They only run through `start(tags)` and auto-launch
     * ([TapTargetController.autoStartTags]), e.g. for a screen the full tour cannot reach. Their
     * tags are part of [TapTargetTour.allTags]; a full start neither shows them nor reports their
     * tags as seen.
     *
     * ```kotlin
     * notInFullTour {
     *     highlightStep("group_members", route = Route.GroupDetails, tags = setOf("group-details")) { ... }
     * }
     * ```
     *
     * @param builder  Adds the steps, like the [tapTargetTour] lambda.
     */
    fun notInFullTour(builder: TourBuilder.() -> Unit) {
        val outer = inFullTour
        inFullTour = false
        try {
            builder()
        } finally {
            inFullTour = outer
        }
    }

    /**
     * Adds a step that spotlights the composable registered with [tapTarget] ([id]) and waits for
     * a tap on it. The rest of the screen is dimmed and blocked.
     *
     * ```kotlin
     * tapStep(id = "new_chat", route = Route.ChatSelector) {
     *     TourTitle("Start chatting")
     *     Row { Icon(Icons.Default.Add, null); TourDescription("Tap here to begin") }
     * }
     * ```
     *
     * @param id               Id of the target, the same string as in [tapTarget].
     * @param route            Route of the screen the target lives on. When another screen shows,
     *                         the controller navigates there first (`onNavigateToRoute` of
     *                         [rememberTapTargetController]). Null: no navigation.
     * @param backgroundColor  Scrim color of this step. Unspecified: [TourSettings.scrimColor].
     * @param requireExactTap  True (the default): only a tap on the target advances; taps next to
     *                         it give [TourSettings.wrongTapFeedback]. False: a tap anywhere advances.
     * @param passTapToTarget  True: the tap on the target also reaches the real composable (the
     *                         button runs its onClick) and the tour advances. False: the overlay
     *                         catches the tap. Null (the default): [TourSettings.passTapToTarget].
     * @param spotlightShape   Shape of the spotlight. Null (the default): [TourSettings.spotlightShape].
     * @param beforeShow       Runs after the navigation and before the step waits for its target,
     *                         e.g. to scroll a lazy list to an item that is not composed yet.
     * @param tags             Free strings that pick this step for `start(tags)` and for
     *                         auto-launch ([TapTargetController.autoStartTags]), e.g. "chat". Empty
     *                         (the default): only a full start runs it.
     * @param dependsOn        Id of an earlier tap or highlight step this step needs. When that
     *                         step was passed over in this run, this step is passed over at once
     *                         and stays unseen. See [TourStep.dependsOn].
     * @param targetTimeout    How long this step waits for its target. [Duration.ZERO]: no waiting.
     *                         Null (the default): the [TourSettings] timeouts.
     * @param content          Content of the bubble next to the spotlight. Use [TourTitle] and
     *                         [TourDescription] for the default text styles.
     */
    fun tapStep(
        id: String,
        route: Any? = null,
        backgroundColor: Color = Color.Unspecified,
        requireExactTap: Boolean = true,
        passTapToTarget: Boolean? = null,
        spotlightShape: SpotlightShape? = null,
        beforeShow: (suspend () -> Unit)? = null,
        tags: Set<String> = emptySet(),
        dependsOn: String? = null,
        targetTimeout: Duration? = null,
        content: TourStepContent,
    ) {
        steps += TourStep(
            id = id,
            route = route,
            backgroundColor = backgroundColor,
            requireExactTap = requireExactTap,
            passTapToTarget = passTapToTarget,
            spotlightShape = spotlightShape,
            beforeShow = beforeShow,
            tags = tags,
            dependsOn = dependsOn,
            targetTimeout = targetTimeout,
            inFullTour = inFullTour,
            section = currentSection,
            content = content,
        )
    }

    /**
     * Adds a step that spotlights the composable registered with [tapTarget] ([id]) and waits for
     * a tap on it, with a title and description from string resources. The rest of the screen is
     * dimmed and blocked.
     *
     * ```kotlin
     * tapStep("new_chat", title = Res.string.start_chatting, description = Res.string.start_chatting_desc)
     * ```
     *
     * @param id               Id of the target, the same string as in [tapTarget].
     * @param title            Headline of the bubble ([TourTitle]). Null: no headline.
     * @param description      Body text of the bubble ([TourDescription]). Null: no body text. With
     *                         both null the bubble only shows the tap hint.
     * @param route            Route of the screen the target lives on. When another screen shows,
     *                         the controller navigates there first (`onNavigateToRoute` of
     *                         [rememberTapTargetController]). Null: no navigation.
     * @param backgroundColor  Scrim color of this step. Unspecified: [TourSettings.scrimColor].
     * @param requireExactTap  True (the default): only a tap on the target advances; taps next to
     *                         it give [TourSettings.wrongTapFeedback]. False: a tap anywhere advances.
     * @param passTapToTarget  True: the tap on the target also reaches the real composable (the
     *                         button runs its onClick) and the tour advances. False: the overlay
     *                         catches the tap. Null (the default): [TourSettings.passTapToTarget].
     * @param beforeShow       Runs after the navigation and before the step waits for its target,
     *                         e.g. to scroll a lazy list to an item that is not composed yet.
     * @param spotlightShape   Shape of the spotlight. Null (the default): [TourSettings.spotlightShape].
     * @param tags             Free strings that pick this step for `start(tags)` and for
     *                         auto-launch ([TapTargetController.autoStartTags]), e.g. "chat". Empty
     *                         (the default): only a full start runs it.
     * @param dependsOn        Id of an earlier tap or highlight step this step needs. When that
     *                         step was passed over in this run, this step is passed over at once
     *                         and stays unseen. See [TourStep.dependsOn].
     * @param targetTimeout    How long this step waits for its target. [Duration.ZERO]: no waiting.
     *                         Null (the default): the [TourSettings] timeouts.
     */
    fun tapStep(
        id: String,
        title: StringResource? = null,
        description: StringResource? = null,
        route: Any? = null,
        backgroundColor: Color = Color.Unspecified,
        requireExactTap: Boolean = true,
        passTapToTarget: Boolean? = null,
        beforeShow: (suspend () -> Unit)? = null,
        // Not a function type as last parameter, so a trailing lambda always picks the slot variant
        spotlightShape: SpotlightShape? = null,
        tags: Set<String> = emptySet(),
        dependsOn: String? = null,
        targetTimeout: Duration? = null,
    ) {
        steps += TourStep(
            id = id,
            route = route,
            backgroundColor = backgroundColor,
            requireExactTap = requireExactTap,
            passTapToTarget = passTapToTarget,
            spotlightShape = spotlightShape,
            beforeShow = beforeShow,
            tags = tags,
            dependsOn = dependsOn,
            targetTimeout = targetTimeout,
            inFullTour = inFullTour,
            section = currentSection,
            content = resourceContent(title, description),
        )
    }

    /**
     * Adds a step that only points at the composable registered with [tapTarget] ([id]), for
     * information: the outline is calm and dashed (no pulse), the cutout stays lightly dimmed
     * ([TourSettings.highlightCutoutScrim]), the bubble has an info icon and a "Got it" button
     * ([buttonText], default [TourStrings.gotIt]). A tap on the target does nothing (it never
     * reaches the element), a tap anywhere else continues.
     * Use it for elements the user should know about but not press now.
     *
     * @param id               Id of the target, the same string as in [tapTarget].
     * @param route            Route of the screen the target lives on. When another screen shows,
     *                         the controller navigates there first (`onNavigateToRoute` of
     *                         [rememberTapTargetController]). Null: no navigation.
     * @param backgroundColor  Scrim color of this step. Unspecified: [TourSettings.scrimColor].
     * @param spotlightShape   Shape of the spotlight. Null (the default): [TourSettings.spotlightShape].
     * @param buttonText       Label of the button that continues. Null: [TourStrings.gotIt].
     * @param beforeShow       Runs after the navigation and before the step waits for its target,
     *                         e.g. to scroll a lazy list to an item that is not composed yet.
     * @param tags             Free strings that pick this step for `start(tags)` and for
     *                         auto-launch ([TapTargetController.autoStartTags]), e.g. "chat". Empty
     *                         (the default): only a full start runs it.
     * @param dependsOn        Id of an earlier tap or highlight step this step needs. When that
     *                         step was passed over in this run, this step is passed over at once
     *                         and stays unseen. See [TourStep.dependsOn].
     * @param targetTimeout    How long this step waits for its target. [Duration.ZERO]: no waiting.
     *                         Null (the default): the [TourSettings] timeouts.
     * @param content          Content of the bubble, shown next to an info icon. Use [TourTitle] and
     *                         [TourDescription] for the default text styles.
     */
    fun highlightStep(
        id: String,
        route: Any? = null,
        backgroundColor: Color = Color.Unspecified,
        spotlightShape: SpotlightShape? = null,
        buttonText: String? = null,
        beforeShow: (suspend () -> Unit)? = null,
        tags: Set<String> = emptySet(),
        dependsOn: String? = null,
        targetTimeout: Duration? = null,
        content: TourStepContent,
    ) {
        steps += TourStep(
            id = id,
            route = route,
            backgroundColor = backgroundColor,
            continueButtonText = buttonText?.let { text -> { text } },
            requireExactTap = false,
            spotlightShape = spotlightShape,
            beforeShow = beforeShow,
            highlightOnly = true,
            tags = tags,
            dependsOn = dependsOn,
            targetTimeout = targetTimeout,
            inFullTour = inFullTour,
            section = currentSection,
            content = content,
        )
    }

    /**
     * Adds a step that only points at the composable registered with [tapTarget] ([id]), for
     * information, with a title and description from string resources: the outline is calm and
     * dashed (no pulse), the cutout stays lightly dimmed ([TourSettings.highlightCutoutScrim]), the
     * bubble has an info icon and a "Got it" button. A tap on the target does nothing (it never
     * reaches the element), a tap anywhere else continues.
     * Use it for elements the user should know about but not press now.
     *
     * @param id               Id of the target, the same string as in [tapTarget].
     * @param title            Headline of the bubble ([TourTitle]). Null: no headline.
     * @param description      Body text of the bubble ([TourDescription]). Null: no body text.
     * @param route            Route of the screen the target lives on. When another screen shows,
     *                         the controller navigates there first (`onNavigateToRoute` of
     *                         [rememberTapTargetController]). Null: no navigation.
     * @param backgroundColor  Scrim color of this step. Unspecified: [TourSettings.scrimColor].
     * @param buttonText       Label of the button that continues. Null: [TourStrings.gotIt].
     * @param beforeShow       Runs after the navigation and before the step waits for its target,
     *                         e.g. to scroll a lazy list to an item that is not composed yet.
     * @param spotlightShape   Shape of the spotlight. Null (the default): [TourSettings.spotlightShape].
     * @param tags             Free strings that pick this step for `start(tags)` and for
     *                         auto-launch ([TapTargetController.autoStartTags]), e.g. "chat". Empty
     *                         (the default): only a full start runs it.
     * @param dependsOn        Id of an earlier tap or highlight step this step needs. When that
     *                         step was passed over in this run, this step is passed over at once
     *                         and stays unseen. See [TourStep.dependsOn].
     * @param targetTimeout    How long this step waits for its target. [Duration.ZERO]: no waiting.
     *                         Null (the default): the [TourSettings] timeouts.
     */
    fun highlightStep(
        id: String,
        title: StringResource? = null,
        description: StringResource? = null,
        route: Any? = null,
        backgroundColor: Color = Color.Unspecified,
        buttonText: StringResource? = null,
        beforeShow: (suspend () -> Unit)? = null,
        // Not a function type as last parameter, so a trailing lambda always picks the slot variant
        spotlightShape: SpotlightShape? = null,
        tags: Set<String> = emptySet(),
        dependsOn: String? = null,
        targetTimeout: Duration? = null,
    ) {
        steps += TourStep(
            id = id,
            route = route,
            backgroundColor = backgroundColor,
            continueButtonText = buttonText?.let { res -> { stringResource(res) } },
            requireExactTap = false,
            spotlightShape = spotlightShape,
            beforeShow = beforeShow,
            highlightOnly = true,
            tags = tags,
            dependsOn = dependsOn,
            targetTimeout = targetTimeout,
            inFullTour = inFullTour,
            section = currentSection,
            content = resourceContent(title, description),
        )
    }

    /**
     * Adds a step without spotlight: full scrim, card centered on screen, a tap anywhere advances.
     * Use it for a welcome or a closing message.
     *
     * @param route            Route of the screen to show behind the card. When another screen
     *                         shows, the controller navigates there first. Null: no navigation.
     * @param backgroundColor  Scrim color of this step. Unspecified: [TourSettings.scrimColor].
     * @param beforeShow       Runs after the navigation and before the step shows.
     * @param tags             Free strings that pick this step for `start(tags)` and for
     *                         auto-launch ([TapTargetController.autoStartTags]), e.g. "chat". Empty
     *                         (the default): only a full start runs it.
     * @param dependsOn        Id of an earlier tap or highlight step this step needs. When that
     *                         step was passed over in this run, this step is passed over at once
     *                         and stays unseen. See [TourStep.dependsOn].
     * @param content          Content of the centered card. Use [TourTitle] and [TourDescription]
     *                         for the default text styles.
     */
    fun infoStep(
        route: Any? = null,
        backgroundColor: Color = Color.Unspecified,
        beforeShow: (suspend () -> Unit)? = null,
        tags: Set<String> = emptySet(),
        dependsOn: String? = null,
        content: TourStepContent,
    ) {
        steps += TourStep(
            id = null,
            route = route,
            backgroundColor = backgroundColor,
            beforeShow = beforeShow,
            tags = tags,
            dependsOn = dependsOn,
            inFullTour = inFullTour,
            section = currentSection,
            content = content,
        )
    }

    /**
     * Adds a step without spotlight, with a title and description from string resources: full
     * scrim, card centered on screen, a tap anywhere advances.
     * Use it for a welcome or a closing message.
     *
     * @param title            Headline of the card ([TourTitle]). Null: no headline.
     * @param description      Body text of the card ([TourDescription]). Null: no body text. With
     *                         both null the card only shows the tap hint.
     * @param route            Route of the screen to show behind the card. When another screen
     *                         shows, the controller navigates there first. Null: no navigation.
     * @param beforeShow       Runs after the navigation and before the step shows.
     * @param backgroundColor  Scrim color of this step. Unspecified: [TourSettings.scrimColor].
     * @param tags             Free strings that pick this step for `start(tags)` and for
     *                         auto-launch ([TapTargetController.autoStartTags]), e.g. "chat". Empty
     *                         (the default): only a full start runs it.
     * @param dependsOn        Id of an earlier tap or highlight step this step needs. When that
     *                         step was passed over in this run, this step is passed over at once
     *                         and stays unseen. See [TourStep.dependsOn].
     */
    fun infoStep(
        title: StringResource? = null,
        description: StringResource? = null,
        route: Any? = null,
        beforeShow: (suspend () -> Unit)? = null,
        backgroundColor: Color = Color.Unspecified,
        tags: Set<String> = emptySet(),
        dependsOn: String? = null,
    ) {
        steps += TourStep(
            id = null,
            route = route,
            backgroundColor = backgroundColor,
            beforeShow = beforeShow,
            tags = tags,
            dependsOn = dependsOn,
            inFullTour = inFullTour,
            section = currentSection,
            content = resourceContent(title, description),
        )
    }

    /**
     * Adds a step that lets the user freely explore the app while a small, non-blocking
     * hint bar stays anchored at [position]. Unlike [tapStep]/[infoStep], tapping
     * elsewhere on screen does nothing — the tour only advances when the user presses
     * the bar's Continue button.
     *
     * @param route               Route of the screen the step starts on. When another screen shows,
     *                            the controller navigates there first. With a route,
     *                            [TourSettings.freeRoamLeaveBehavior] decides what happens when the
     *                            user leaves that screen. Null: no navigation, the user may go anywhere.
     * @param position            Where the bar sits, see [FreeRoamBarPosition]. Default: bottom.
     * @param continueButtonText  Label of the Continue button. Null: [TourStrings.continueButton].
     * @param beforeShow          Runs after the navigation and before the step shows.
     * @param tags                Free strings that pick this step for `start(tags)` and for
     *                            auto-launch ([TapTargetController.autoStartTags]), e.g. "chat".
     *                            Empty (the default): only a full start runs it.
     * @param dependsOn           Id of an earlier tap or highlight step this step needs. When that
     *                            step was passed over in this run, this step is passed over at once
     *                            and stays unseen. See [TourStep.dependsOn].
     * @param content             Content of the bar, above the Continue button. Use [TourTitle] and
     *                            [TourDescription] for the default text styles.
     */
    fun freeRoamStep(
        route: Any? = null,
        position: FreeRoamBarPosition = FreeRoamBarPosition.Bottom,
        continueButtonText: String? = null,
        beforeShow: (suspend () -> Unit)? = null,
        tags: Set<String> = emptySet(),
        dependsOn: String? = null,
        content: TourStepContent,
    ) {
        steps += TourStep(
            id = null,
            route = route,
            freeRoamPosition = position,
            continueButtonText = continueButtonText?.let { text -> { text } },
            beforeShow = beforeShow,
            tags = tags,
            dependsOn = dependsOn,
            inFullTour = inFullTour,
            section = currentSection,
            content = content,
        )
    }

    /**
     * Adds a step that lets the user freely explore the app while a small, non-blocking hint bar
     * with a title and description from string resources stays anchored at [position]. Tapping
     * elsewhere on screen does nothing — the tour only advances when the user presses the bar's
     * Continue button.
     *
     * @param title               Headline of the bar ([TourTitle]). Null: no headline.
     * @param description         Body text of the bar ([TourDescription]). Null: no body text.
     * @param route               Route of the screen the step starts on. When another screen shows,
     *                            the controller navigates there first. With a route,
     *                            [TourSettings.freeRoamLeaveBehavior] decides what happens when the
     *                            user leaves that screen. Null: no navigation, the user may go anywhere.
     * @param position            Where the bar sits, see [FreeRoamBarPosition]. Default: bottom.
     * @param beforeShow          Runs after the navigation and before the step shows.
     * @param continueButtonText  Label of the Continue button. Null: [TourStrings.continueButton].
     * @param tags                Free strings that pick this step for `start(tags)` and for
     *                            auto-launch ([TapTargetController.autoStartTags]), e.g. "chat".
     *                            Empty (the default): only a full start runs it.
     * @param dependsOn           Id of an earlier tap or highlight step this step needs. When that
     *                            step was passed over in this run, this step is passed over at once
     *                            and stays unseen. See [TourStep.dependsOn].
     */
    fun freeRoamStep(
        title: StringResource? = null,
        description: StringResource? = null,
        route: Any? = null,
        position: FreeRoamBarPosition = FreeRoamBarPosition.Bottom,
        beforeShow: (suspend () -> Unit)? = null,
        continueButtonText: StringResource? = null,
        tags: Set<String> = emptySet(),
        dependsOn: String? = null,
    ) {
        steps += TourStep(
            id = null,
            route = route,
            freeRoamPosition = position,
            continueButtonText = continueButtonText?.let { res -> { stringResource(res) } },
            beforeShow = beforeShow,
            tags = tags,
            dependsOn = dependsOn,
            inFullTour = inFullTour,
            section = currentSection,
            content = resourceContent(title, description),
        )
    }

    private fun resourceContent(title: StringResource?, description: StringResource?): TourStepContent? {
        if (title == null && description == null) return null
        return {
            title?.let { TourTitle(stringResource(it)) }
            description?.let { TourDescription(stringResource(it)) }
        }
    }
}

/**
 * Entry point for defining a tour.
 *
 * ```kotlin
 * val onboardingTour = tapTargetTour {
 *     tapStep("new_chat", title = Res.string.start_chatting, description = Res.string.start_chatting_desc)
 *     tapStep("settings", route = Route.Settings) { TourTitle("Settings") }
 * }
 * ```
 *
 * Define the tour once (e.g. as a top-level `val`): [rememberTapTargetController] reads it only
 * when the controller is created.
 *
 * @param builder  Adds the steps in the order they show, see [TourBuilder].
 * @return The finished tour, to pass to [rememberTapTargetController].
 */
fun tapTargetTour(builder: TourBuilder.() -> Unit): TapTargetTour =
    TapTargetTour(TourBuilder().apply(builder).steps)
