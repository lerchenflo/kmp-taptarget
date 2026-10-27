package io.github.lerchenflo.taptarget

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// ─────────────────────────────────────────────────────────────────
// Tour DSL
// ─────────────────────────────────────────────────────────────────

enum class FreeRoamBarPosition { Top, Center, Bottom }

/**
 * Content of the info bubble of a step. Runs inside a [androidx.compose.foundation.layout.Column]
 * with a small vertical spacing, so any composables (icons, rows, images...) can be placed here.
 * Use [TourTitle] and [TourDescription] for the default text styling.
 */
typealias TourStepContent = @Composable ColumnScope.() -> Unit

/** Light enough that the screen behind stays readable; the spotlight outline carries the focus. */
val DefaultTourBackgroundColor: Color = Color.Black.copy(alpha = 0.55f)

/**
 * Describes a single step in an onboarding tour.
 *
 * @param id               Unique identifier; must match the id passed to [tapTarget].
 *                         Null for steps without a spotlight target (info / free-roam steps).
 * @param route            Optional route of the screen the step lives on. When set, the controller
 *                         navigates to this route before the step is displayed so that the target
 *                         composable is guaranteed to be in the composition.
 * @param backgroundColor  Scrim color. Unspecified: [TourSettings.scrimColor].
 * @param passTapToTarget  Overrides [TourSettings.passTapToTarget] for this step. Null: use the setting.
 * @param spotlightShape   Overrides [TourSettings.spotlightShape] for this step. Null: use the setting.
 * @param highlightOnly    The step only shows the target: calm dashed outline, a "Got it" button,
 *                         a tap anywhere continues. See [TourBuilder.highlightStep].
 * @param beforeShow       Runs after the navigation to [route] and before the step waits for its
 *                         target, e.g. to scroll a lazy list to an item that is not composed yet.
 * @param content          Optional content of the info bubble.
 */
class TourStep(
    val id: String?,
    val route: Any? = null,
    val backgroundColor: Color = Color.Unspecified,
    /** Non-null marks this a free-roam step: shows a non-blocking hint bar at this
     *  position instead of a spotlight or blocking dialog. Everything else on screen
     *  stays fully interactive; only the bar's Continue button advances the tour. */
    val freeRoamPosition: FreeRoamBarPosition? = null,
    /** Continue button label. Falls back to [TourStrings.continueButton] if null. */
    val continueButtonText: (@Composable () -> String)? = null,
    /** When true the user must tap exactly on the highlighted target to advance.
     *  When false the user can tap anywhere on the overlay to advance — useful for
     *  steps that just show a button without requiring the user to press it. */
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

/** Immutable tour description produced by [tapTargetTour]. */
class TapTargetTour(val steps: List<TourStep>)

/**
 * Builder populated inside the [tapTargetTour] lambda.
 *
 * Every step type has two variants:
 * - a [StringResource] variant with `title` / `description` for plain text steps
 * - a slot variant with a trailing [TourStepContent] lambda for custom content (icons etc.)
 *
 * See [TourStep] for the parameters.
 */
class TourBuilder {
    val steps = mutableListOf<TourStep>()

    /**
     * Adds a step that spotlights the composable registered with [tapTarget] ([id]).
     *
     * ```kotlin
     * tapStep(id = "new_chat", route = Route.ChatSelector) {
     *     TourTitle("Start chatting")
     *     Row { Icon(Icons.Default.Add, null); TourDescription("Tap here to begin") }
     * }
     * ```
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
     * information: the outline is calm and dashed (no pulse), the bubble has an info icon and a
     * "Got it" button ([buttonText], default [TourStrings.gotIt]), and a tap anywhere continues.
     * Use it for elements the user should know about but not press now.
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

    /** Adds a step without spotlight: full scrim, bubble centered on screen, tap anywhere advances. */
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
 */
fun tapTargetTour(builder: TourBuilder.() -> Unit): TapTargetTour =
    TapTargetTour(TourBuilder().apply(builder).steps)
