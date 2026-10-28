package io.github.lerchenflo.taptarget

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
    val content: TourStepContent? = null,
)

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
class TapTargetTour(val steps: List<TourStep>)

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
    ) {
        steps += TourStep(
            id = id,
            route = route,
            backgroundColor = backgroundColor,
            requireExactTap = requireExactTap,
            passTapToTarget = passTapToTarget,
            spotlightShape = spotlightShape,
            beforeShow = beforeShow,
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
     * @param content          Content of the centered card. Use [TourTitle] and [TourDescription]
     *                         for the default text styles.
     */
    fun infoStep(
        route: Any? = null,
        backgroundColor: Color = Color.Unspecified,
        beforeShow: (suspend () -> Unit)? = null,
        content: TourStepContent,
    ) {
        steps += TourStep(
            id = null,
            route = route,
            backgroundColor = backgroundColor,
            beforeShow = beforeShow,
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
     */
    fun infoStep(
        title: StringResource? = null,
        description: StringResource? = null,
        route: Any? = null,
        beforeShow: (suspend () -> Unit)? = null,
        backgroundColor: Color = Color.Unspecified,
    ) {
        steps += TourStep(
            id = null,
            route = route,
            backgroundColor = backgroundColor,
            beforeShow = beforeShow,
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
     * @param content             Content of the bar, above the Continue button. Use [TourTitle] and
     *                            [TourDescription] for the default text styles.
     */
    fun freeRoamStep(
        route: Any? = null,
        position: FreeRoamBarPosition = FreeRoamBarPosition.Bottom,
        continueButtonText: String? = null,
        beforeShow: (suspend () -> Unit)? = null,
        content: TourStepContent,
    ) {
        steps += TourStep(
            id = null,
            route = route,
            freeRoamPosition = position,
            continueButtonText = continueButtonText?.let { text -> { text } },
            beforeShow = beforeShow,
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
     */
    fun freeRoamStep(
        title: StringResource? = null,
        description: StringResource? = null,
        route: Any? = null,
        position: FreeRoamBarPosition = FreeRoamBarPosition.Bottom,
        beforeShow: (suspend () -> Unit)? = null,
        continueButtonText: StringResource? = null,
    ) {
        steps += TourStep(
            id = null,
            route = route,
            freeRoamPosition = position,
            continueButtonText = continueButtonText?.let { res -> { stringResource(res) } },
            beforeShow = beforeShow,
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
