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
 * @param modifier    Applied to the full-size [Box] that holds the content and the overlay.
 * @param strings     Built-in overlay texts, see [TourStrings].
 * @param skipButton      Optional skip button slot, see [TourSkipButton]. Null hides the button.
 * @param continueButton  Optional Continue button of free-roam steps. Null: a material3 button.
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
