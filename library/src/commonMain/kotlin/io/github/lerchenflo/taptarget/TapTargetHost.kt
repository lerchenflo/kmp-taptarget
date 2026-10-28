package io.github.lerchenflo.taptarget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * Root of a tour. Wrap your app content in it once:
 *
 * ```kotlin
 * TapTargetHost(controller = tourController) {
 *     AppNavHost()
 * }
 * ```
 *
 * It does what a tour needs at the root, so the app does not wire them by hand:
 * - provides [controller] to [content], so [tapTarget] finds it and [LocalTapTargetController]
 *   returns it (e.g. for a "restart tour" button)
 * - draws [TapTargetOverlay] on top of [content]
 * - hides [content] from screen readers while a blocking step shows
 *   ([TourSettings.hideAppFromScreenReader])
 *
 * @param controller      The controller from [rememberTapTargetController].
 * @param modifier        Applied to the full-size [Box] that holds the content and the overlay.
 * @param strings         Built-in overlay texts (tap hints, button labels, screen reader texts),
 *                        see [TourStrings]. Pass localized ones here.
 * @param skipButton      Skip button, shown on every step in a corner of the screen half away from
 *                        the spotlight. It gets `onSkip`, which ends the tour
 *                        ([TapTargetController.skip]). Use [TourSkipButton] for the default look.
 *                        Null (the default) shows no skip button.
 * @param continueButton  Replaces the button of free-roam steps ("Continue") and highlight steps
 *                        ("Got it"). It gets the button label and `onContinue`, which goes to the
 *                        next step. Null (the default): a material3 [androidx.compose.material3.Button]
 *                        on free-roam steps, a [androidx.compose.material3.FilledTonalButton] on
 *                        highlight steps.
 * @param content         The app, usually the navigation host. Placed in a full-size [Box] below
 *                        the overlay.
 */
@Composable
fun TapTargetHost(
    controller: TapTargetController,
    modifier: Modifier = Modifier,
    strings: TourStrings = TourStrings(),
    skipButton: (@Composable (onSkip: () -> Unit) -> Unit)? = null,
    continueButton: (@Composable (text: String, onContinue: () -> Unit) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val hideContent = controller.tourSettings.hideAppFromScreenReader && controller.isBlocking
    CompositionLocalProvider(LocalTapTargetController provides controller) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hideContent) Modifier.clearAndSetSemantics {} else Modifier),
                content = content,
            )

            //Last child, so it is drawn above everything else
            TapTargetOverlay(
                controller = controller,
                strings = strings,
                skipButton = skipButton,
                continueButton = continueButton,
            )
        }
    }
}
