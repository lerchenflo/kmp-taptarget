package io.github.lerchenflo.taptarget

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Texts the overlay shows by itself. Defaults are English, pass localized ones to
 * [TapTargetHost] (e.g. resolved via `stringResource` at the call site).
 *
 * @param tapHighlighted     Hint on steps that need a tap on the highlighted target.
 * @param tapAnywhere        Hint on steps that continue with a tap anywhere.
 * @param continueButton     Label of the free-roam Continue button (a step can override it).
 * @param skipTour           Screen reader action that skips the tour.
 * @param previousStep       Screen reader action that goes back one step.
 * @param highlightedTarget  Screen reader description of the highlighted area.
 * @param stepProgress       Progress text, shown when [BubbleStyle.showProgress] is on.
 * @param gotIt              Button of [TourBuilder.highlightStep] steps (a step can override it).
 */
@Immutable
data class TourStrings(
    val tapHighlighted: String = "Tap the highlighted area to continue",
    val tapAnywhere: String = "Tap anywhere to continue",
    val continueButton: String = "Continue",
    val skipTour: String = "Skip tour",
    val previousStep: String = "Back",
    val highlightedTarget: String = "Highlighted element",
    val stepProgress: (current: Int, total: Int) -> String = { current, total -> "$current / $total" },
    val gotIt: String = "Got it",
)

/** Shape of the hole the spotlight cuts around a target. */
enum class SpotlightShape {
    /** Rounded rectangle around the target bounds, see [TourSettings.cornerRadius]. */
    RoundedRect,

    /**
     * Circle around the whole target (corners included). Fits round icons and square buttons.
     * A target that is clearly wider than tall (or taller than wide, ratio above 1.25) gets a
     * [Pill] instead, because a circle around it would cover far too much of the screen.
     */
    Circle,

    /** Capsule: a rectangle with fully rounded ends. Fits chips, text buttons and list rows. */
    Pill,

    /** Ellipse through the target's corners. Follows the target's proportions. */
    Oval,
}

/** What the system back button / gesture does while a blocking step shows. */
enum class TourBackBehavior {
    /** Go back one step. Does nothing on the first step. */
    PreviousStep,

    /** Ignore back, the user stays on the step. */
    Block,

    /** End the tour, like the skip button. */
    SkipTour,

    /** Do not intercept back: the app handles it as if there was no tour. */
    PassThrough,
}

/**
 * What happens when the user leaves the screen of a free-roam step (app back arrow, a tab, a link).
 * Only applies to free-roam steps with a `route`.
 */
enum class FreeRoamLeaveBehavior {
    /** Nothing: the user may explore other screens, the hint bar stays. */
    Allow,

    /** Navigate back to the step's route. */
    ReturnToStepRoute,

    /** Go on with the next step. */
    NextStep,

    /** End the tour. */
    SkipTour,
}

/**
 * Feedback for a tap next to the target on a step that needs an exact tap.
 * Every part can be switched off on its own, [None] switches everything off.
 *
 * @param tapIndicator    Expanding circle with a cross where the user tapped.
 * @param shakeBubble     Shakes the info bubble horizontally.
 * @param flashHint       Flashes the tap hint in the bubble in [color].
 * @param haptic          Short "reject" vibration, where the platform supports it.
 * @param pulseTarget     Makes the ring around the target pulse once.
 * @param showGuideAfter  After this many wrong taps in a row, a hand moves from the last wrong tap
 *                        to the target. 0 never shows it.
 * @param color           Color of the indicator and the flashing hint.
 *                        Unspecified: `MaterialTheme.colorScheme.error`.
 */
@Immutable
data class WrongTapFeedback(
    val tapIndicator: Boolean = true,
    val shakeBubble: Boolean = true,
    val flashHint: Boolean = true,
    val haptic: Boolean = true,
    val pulseTarget: Boolean = true,
    val showGuideAfter: Int = 3,
    val color: Color = Color.Unspecified,
) {
    companion object {
        /** No feedback at all: wrong taps are ignored silently. */
        val None = WrongTapFeedback(
            tapIndicator = false,
            shakeBubble = false,
            flashHint = false,
            haptic = false,
            pulseTarget = false,
            showGuideAfter = 0,
        )
    }
}

/**
 * Look of the info bubble (spotlight steps), the centered card (info steps) and the free-roam bar.
 *
 * @param maxWidth          Maximum width of the bubble next to a spotlight.
 * @param centeredMaxWidth  Maximum width of the centered card of info steps.
 * @param shape             Shape of bubble, card and bar.
 * @param containerColor    Background. Unspecified: `MaterialTheme.colorScheme.surface`.
 * @param contentColor      Text and icon color. Unspecified: `contentColorFor(containerColor)`.
 * @param shadowElevation   Shadow of bubble, card and bar.
 * @param contentPadding    Padding inside bubble, card and bar.
 * @param gap               Distance between the spotlight and the bubble.
 * @param showTapHint       Shows the "tap the highlighted area / tap anywhere" row.
 * @param tapHintIcon       Icon of the tap hint row.
 * @param showProgress      Shows the step progress text ([TourStrings.stepProgress]).
 */
@Immutable
data class BubbleStyle(
    val maxWidth: Dp = 260.dp,
    val centeredMaxWidth: Dp = 280.dp,
    val shape: Shape = RoundedCornerShape(12.dp),
    val containerColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
    val shadowElevation: Dp = 4.dp,
    val contentPadding: PaddingValues = PaddingValues(12.dp),
    val gap: Dp = 16.dp,
    val showTapHint: Boolean = true,
    val tapHintIcon: ImageVector = Icons.Filled.TouchApp,
    val showProgress: Boolean = false,
)

/**
 * Behaviour and look of a tour. Pass it to [rememberTapTargetController]; it can be changed while
 * the tour runs (see [TapTargetController.tourSettings]).
 *
 * @param iconPadding              Space between the target bounds and the spotlight edge.
 * @param cornerRadius             Corner radius of a [SpotlightShape.RoundedRect] spotlight.
 * @param spotlightShape           Default shape of the spotlight. A step can override it.
 * @param scrimColor               Default scrim color. A step can override it.
 * @param highlightColor           Spotlight outline, tap ring and guide hand.
 *                                 Unspecified: `MaterialTheme.colorScheme.primary`.
 * @param spotlightBreathing       The spotlight slowly grows and shrinks, so the eye finds it.
 * @param showTapRing              Pulsing ring around the target on steps that need an exact tap.
 * @param dashedHighlightOutline   [TourBuilder.highlightStep] steps get a dashed outline (else solid).
 * @param highlightCutoutScrim     Scrim inside the cutout of [TourBuilder.highlightStep] steps, as a
 *                                 fraction of the scrim's opacity: 0 = fully clear, 1 = as dark as
 *                                 the rest. Shows that the element is only shown, not to be pressed.
 * @param highlightCutoutColor     Color of that cutout scrim. Unspecified: the step's scrim color.
 *                                 A gray (e.g. `MaterialTheme.colorScheme.outline`) greys the element out.
 * @param outlineWidth             Width of the spotlight outline.
 * @param highlightOutlineWidth    Width of the outline of [TourBuilder.highlightStep] steps.
 * @param skipMissingTargets       Skip a step whose target does not appear within [targetTimeout],
 *                                 instead of waiting forever.
 * @param targetTimeout            How long a step waits for its target.
 * @param autoScroll               Scroll a target inside a scrollable container to the middle of
 *                                 the screen.
 * @param passTapToTarget          A tap on the target also reaches the real composable (the button
 *                                 runs its own onClick). A step can override it.
 * @param wrongTapFeedback         Feedback for taps next to the target.
 * @param backBehavior             What system back does while a blocking step shows.
 * @param freeRoamBackBehavior     What system back does during a free-roam step. Default
 *                                 [TourBackBehavior.PassThrough]: the app handles it.
 * @param freeRoamLeaveBehavior    What happens when the user leaves the screen of a free-roam step.
 * @param keyboardNavigation       Enter / Space / Right arrow continue, Left arrow goes back,
 *                                 Escape skips the tour.
 * @param hideAppFromScreenReader  While a blocking step shows, screen readers only see the tour.
 * @param bubble                   Look of bubbles, cards and the free-roam bar.
 */
@Immutable
data class TourSettings(
    val iconPadding: Dp = 4.dp,
    val cornerRadius: Dp = 16.dp,
    val spotlightShape: SpotlightShape = SpotlightShape.RoundedRect,
    val scrimColor: Color = DefaultTourBackgroundColor,
    val highlightColor: Color = Color.Unspecified,
    val spotlightBreathing: Boolean = true,
    val showTapRing: Boolean = true,
    val dashedHighlightOutline: Boolean = true,
    val highlightCutoutScrim: Float = 0.6f,
    val highlightCutoutColor: Color = Color.Unspecified,
    val outlineWidth: Dp = 3.dp,
    val highlightOutlineWidth: Dp = 4.dp,
    val skipMissingTargets: Boolean = true,
    val targetTimeout: Duration = 2.seconds,
    val autoScroll: Boolean = true,
    val passTapToTarget: Boolean = false,
    val wrongTapFeedback: WrongTapFeedback = WrongTapFeedback(),
    val backBehavior: TourBackBehavior = TourBackBehavior.PreviousStep,
    val freeRoamBackBehavior: TourBackBehavior = TourBackBehavior.PassThrough,
    val freeRoamLeaveBehavior: FreeRoamLeaveBehavior = FreeRoamLeaveBehavior.Allow,
    val keyboardNavigation: Boolean = true,
    val hideAppFromScreenReader: Boolean = true,
    val bubble: BubbleStyle = BubbleStyle(),
)
