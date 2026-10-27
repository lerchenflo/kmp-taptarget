package io.github.lerchenflo.taptarget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * Root of a tour. Wrap your app content in it once:
 *
 * ```kotlin
 * TapTargetHost(controller = tourController) {
 *     AppNavHost()
 * }
 * ```
 *
 * It does the two things a tour needs at the root, so the app does not wire them by hand:
 * - provides [controller] to [content], so [tapTarget] finds it and [LocalTapTargetController]
 *   returns it (e.g. for a "restart tour" button)
 * - draws [TapTargetOverlay] on top of [content]
 *
 * @param modifier    Applied to the full-size [Box] that holds the content and the overlay.
 * @param strings     Built-in overlay texts, see [TourStrings].
 * @param skipButton  Optional skip button slot, see [TourSkipButton]. Null hides the button.
 */
@Composable
fun TapTargetHost(
    controller: TapTargetController,
    modifier: Modifier = Modifier,
    strings: TourStrings = TourStrings(),
    skipButton: (@Composable (onSkip: () -> Unit) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalTapTargetController provides controller) {
        Box(modifier = modifier.fillMaxSize()) {
            content()

            //Last child, so it is drawn above everything else
            TapTargetOverlay(controller = controller, strings = strings, skipButton = skipButton)
        }
    }
}
