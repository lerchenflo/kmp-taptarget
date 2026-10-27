package io.github.lerchenflo.taptarget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Default headline text for a step's bubble. */
@Composable
fun TourTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text = text, modifier = modifier, style = MaterialTheme.typography.titleMedium, textAlign = textAlign)
}

/** Default body text for a step's bubble. */
@Composable
fun TourDescription(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text = text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, textAlign = textAlign)
}

/**
 * Full-screen overlay that renders the tour spotlight effect.
 *
 * [TapTargetHost] mounts it for you. Use it directly only when you need full control over the
 * root layout; then mount it **once** as the topmost layer inside the root [Box] that wraps your
 * navigation:
 *
 * ```kotlin
 * CompositionLocalProvider(LocalTapTargetController provides controller) {
 *     Box {
 *         AppNavHost(navController)
 *         TapTargetOverlay(controller)
 *     }
 * }
 * ```
 *
 * The overlay:
 * - Returns immediately when the tour is finished ([TapTargetController.isActive] == false).
 * - Draws nothing for the current frame when the target composable is not yet laid out
 *   (e.g. while navigation is in progress).
 * - Punches a hole in the scrim around the target bounds.
 * - Shows the step's content bubble just below (or above) the spotlight hole.
 * - Advances when the highlighted box is tapped (or anywhere, if the step allows it), and gives
 *   feedback for taps next to the target ([WrongTapFeedback]).
 * - Handles system back ([TourSettings.backBehavior]) and the keyboard
 *   ([TourSettings.keyboardNavigation]) while a blocking step shows.
 *
 * @param strings         Built-in texts, see [TourStrings].
 * @param skipButton      Optional skip button slot, see [TourSkipButton]. Null hides the button.
 * @param continueButton  Optional Continue button of free-roam steps. Null: a material3 [Button].
 */
@Composable
fun TapTargetOverlay(
    controller: TapTargetController,
    strings: TourStrings = TourStrings(),
    skipButton: (@Composable (onSkip: () -> Unit) -> Unit)? = null,
    continueButton: (@Composable (text: String, onContinue: () -> Unit) -> Unit)? = null,
) {
    // Composed before the early return, so the handler stays registered across steps
    TourBackHandler(controller)

    if (!controller.isActive) return

    LaunchedEffect(controller.currentIndex) {
        //Effect gets cancelled when the step changes meanwhile, so this only handles the stuck step
        if (controller.ensureCurrentStepVisible()) {
            controller.notifyStepShown()
            controller.watchFreeRoamLeave()
        } else {
            controller.handleMissingTarget()
        }
    }

    val step = controller.currentStep ?: return
    val settings = controller.tourSettings

    val focusRequester = remember { FocusRequester() }
    val keyboardEnabled = settings.keyboardNavigation && step.isBlocking
    LaunchedEffect(controller.currentIndex, keyboardEnabled) {
        // Fails silently when the window cannot take focus (e.g. while it is in the background)
        if (keyboardEnabled) runCatching { focusRequester.requestFocus() }
    }

    //Plain Box without pointer input - taps still reach the app during free-roam steps
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(
                if (keyboardEnabled) {
                    Modifier
                        .focusRequester(focusRequester)
                        .onKeyEvent { event -> event.type == KeyEventType.KeyDown && controller.handleKey(event.key) }
                        .focusable()
                } else {
                    Modifier
                }
            )
    ) {
        TourStepLayer(controller, step, strings, continueButton)

        if (skipButton != null) {
            val target = controller.currentTarget
            //Spotlight steps show the button only once the spotlight itself is drawn
            if (step.id == null || step.freeRoamPosition != null || target != null) {
                val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                Box(
                    modifier = Modifier
                        .align(skipButtonAlignment(step, target, screenHeightPx))
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(8.dp)
                ) {
                    skipButton { controller.skip() }
                }
            }
        }
    }
}

/**
 * Default skip button for the `skipButton` slot of [TapTargetHost]:
 *
 * ```kotlin
 * TapTargetHost(
 *     controller = controller,
 *     skipButton = { onSkip -> TourSkipButton(stringResource(Res.string.skip), onSkip) }
 * ) { ... }
 * ```
 */
@Composable
fun TourSkipButton(text: String, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onSkip, modifier = modifier) {
        Text(text)
    }
}

/** Enter / Space / Right arrow continue, Left arrow goes back, Escape skips. */
private fun TapTargetController.handleKey(key: Key): Boolean {
    when (key) {
        Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionRight -> next()
        Key.DirectionLeft -> previous()
        Key.Escape -> skip()
        else -> return false
    }
    return true
}

@Composable
private fun TourBackHandler(controller: TapTargetController) {
    // Hosts without a back dispatcher (some tests, custom hosts) get no back handling instead of a crash
    if (LocalNavigationEventDispatcherOwner.current == null) return
    val settings = controller.tourSettings
    val behavior = if (controller.isBlocking) settings.backBehavior else settings.freeRoamBackBehavior
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = controller.isActive && behavior != TourBackBehavior.PassThrough,
        onBackCompleted = {
            when (behavior) {
                TourBackBehavior.PreviousStep -> controller.previous()
                TourBackBehavior.SkipTour -> controller.skip()
                TourBackBehavior.Block, TourBackBehavior.PassThrough -> Unit
            }
        },
    )
}

/** Puts the skip button in the half of the screen opposite to the spotlight / free-roam bar. */
private fun skipButtonAlignment(step: TourStep, target: TargetInfo?, screenHeightPx: Float): Alignment =
    when {
        step.freeRoamPosition == FreeRoamBarPosition.Top -> Alignment.BottomEnd
        step.freeRoamPosition != null -> Alignment.TopEnd
        target != null && target.bounds.center.y < screenHeightPx / 2f -> Alignment.BottomEnd
        else -> Alignment.TopEnd
    }

@Composable
private fun TourStepLayer(
    controller: TapTargetController,
    step: TourStep,
    strings: TourStrings,
    continueButton: (@Composable (text: String, onContinue: () -> Unit) -> Unit)?,
) {
    // Free-roam: non-blocking hint bar, user explores freely, advances via button only.
    if (step.freeRoamPosition != null) {
        FreeRoamTourBar(
            controller = controller,
            step = step,
            position = step.freeRoamPosition,
            strings = strings,
            continueText = step.continueButtonText?.invoke() ?: strings.continueButton,
            continueButton = continueButton,
        )
        return
    }

    // Centered: no spotlight target, full block, tap-anywhere advances.
    if (step.id == null) {
        CenteredTourStep(controller, step, strings)
        return
    }

    SpotlightTourStep(controller, step, strings)
}

/** Animations of the feedback for taps next to the target. Created once per step. */
@Stable
private class WrongTapState {
    var position by mutableStateOf(Offset.Unspecified)
        private set
    var guideStart by mutableStateOf(Offset.Unspecified)
        private set
    private var count = 0

    /** 0 = just tapped, 1 = gone. */
    val indicator = Animatable(1f)
    val targetPulse = Animatable(1f)
    val guide = Animatable(1f)

    /** Horizontal offset of the bubble in px. */
    val shake = Animatable(0f)

    /** 1 = hint fully in the feedback color, 0 = normal. */
    val flash = Animatable(0f)

    fun trigger(position: Offset, feedback: WrongTapFeedback, scope: CoroutineScope, shakePx: Float) {
        this.position = position
        count++
        if (feedback.tapIndicator) scope.launch {
            indicator.snapTo(0f)
            indicator.animateTo(1f, tween(550))
        }
        if (feedback.pulseTarget) scope.launch {
            targetPulse.snapTo(0f)
            targetPulse.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        }
        if (feedback.shakeBubble) scope.launch {
            shake.snapTo(0f)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    shakePx at 50
                    -shakePx at 120
                    shakePx * 0.6f at 190
                    -shakePx * 0.3f at 260
                },
            )
        }
        if (feedback.flashHint) scope.launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(1200))
        }
        if (feedback.showGuideAfter > 0 && count % feedback.showGuideAfter == 0) {
            guideStart = position
            scope.launch {
                guide.snapTo(0f)
                guide.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            }
        }
    }
}

/** A circle only fits targets that are about square; others get a pill (see [SpotlightShape.Circle]). */
private fun resolveSpotlightShape(target: Rect, shape: SpotlightShape): SpotlightShape {
    if (shape != SpotlightShape.Circle) return shape
    val longer = max(target.width, target.height)
    val shorter = min(target.width, target.height)
    return if (shorter > 0f && longer / shorter <= 1.25f) SpotlightShape.Circle else SpotlightShape.Pill
}

/** Bounds of the spotlight hole for a resolved [shape]: the hole always contains the whole target. */
private fun spotlightBounds(target: Rect, shape: SpotlightShape, paddingPx: Float): Rect = when (shape) {
    SpotlightShape.RoundedRect, SpotlightShape.Pill -> target.inflate(paddingPx)
    SpotlightShape.Circle -> Rect(center = target.center, radius = hypot(target.width, target.height) / 2f + paddingPx)
    SpotlightShape.Oval -> {
        // The ellipse with the target's proportions that passes through its corners: half axes * sqrt(2)
        val radiusX = target.width / 2f * SQRT_2 + paddingPx
        val radiusY = target.height / 2f * SQRT_2 + paddingPx
        Rect(target.center.x - radiusX, target.center.y - radiusY, target.center.x + radiusX, target.center.y + radiusY)
    }
}

private const val SQRT_2 = 1.4142135f

private fun spotlightPath(bounds: Rect, shape: SpotlightShape, cornerRadiusPx: Float): Path = Path().apply {
    when (shape) {
        SpotlightShape.RoundedRect -> addRoundRect(RoundRect(rect = bounds, cornerRadius = CornerRadius(cornerRadiusPx)))
        SpotlightShape.Pill -> addRoundRect(RoundRect(rect = bounds, cornerRadius = CornerRadius(min(bounds.width, bounds.height) / 2f)))
        SpotlightShape.Circle, SpotlightShape.Oval -> addOval(bounds)
    }
}

@Composable
private fun SpotlightTourStep(controller: TapTargetController, step: TourStep, strings: TourStrings) {
    val target = controller.currentTarget ?: return   // not laid out yet → draw nothing
    // Bounds of a target that is detached or laid out in an odd place: draw nothing rather than crash
    if (!target.bounds.isFinite) return

    val settings = controller.tourSettings
    val feedback = settings.wrongTapFeedback
    val bubbleStyle = settings.bubble
    val shape = resolveSpotlightShape(target.bounds, controller.spotlightShapeOf(step))
    val passThrough = controller.passesTapToTarget(step)

    val highlightColor = settings.highlightColor.takeOrElse { MaterialTheme.colorScheme.primary }
    val feedbackColor = feedback.color.takeOrElse { MaterialTheme.colorScheme.error }
    val scrimColor = step.backgroundColor.takeOrElse { settings.scrimColor }

    val density = LocalDensity.current
    val spotlight = spotlightBounds(target.bounds, shape, with(density) { settings.iconPadding.toPx() })

    var bubbleSize by remember(step) { mutableStateOf(IntSize.Zero) }
    val wrongTap = remember(step) { WrongTapState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val shakePx = with(density) { 10.dp.toPx() }

    val onTap: (Offset) -> Unit = { position ->
        if (!step.requireExactTap || spotlight.contains(position)) {
            controller.next()
        } else {
            controller.reportWrongTap(position)
            wrongTap.trigger(position, feedback, scope, shakePx)
            if (feedback.haptic) runCatching { haptic.performHapticFeedback(HapticFeedbackType.Reject) }
        }
    }

    // Ambient pulse ring shown only while the user must tap the exact highlighted spot.
    val ringTransition = rememberInfiniteTransition(label = "tapTargetRing")
    val ringProgress by ringTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tapTargetRingProgress",
    )
    // The spotlight itself breathes so the eye finds it at once
    val spotlightPulseProgress by ringTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tapTargetSpotlightPulse",
    )

    // Taps are handled on the parent, not the scrim: the bubble is a sibling above the scrim
    // and its Surface would swallow a tap on it, so "tap anywhere" would not advance there.
    // Pass-through steps leave the hole free instead (see TapCatchersAround).
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(
                if (passThrough) Modifier
                else Modifier.pointerInput(step, spotlight) { detectTapGestures(onTap = onTap) }
            )
    ) {
        // Inside an unbounded container (e.g. a scrolling parent) there is no screen size to lay out against
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) return@BoxWithConstraints
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val edgePaddingPx = with(density) { 16.dp.toPx() }
        val gapPx = with(density) { bubbleStyle.gap.toPx() }
        val cornerRadiusPx = with(density) { settings.cornerRadius.toPx() }
        val ringInflatePx = with(density) { 10.dp.toPx() }
        val ringStrokePx = with(density) { 2.dp.toPx() }
        val outlineStrokePx = with(density) { 3.dp.toPx() }
        val spotlightInflatePx = with(density) { 6.dp.toPx() }
        val indicatorStartPx = with(density) { 6.dp.toPx() }
        val indicatorEndPx = with(density) { 30.dp.toPx() }
        val crossPx = with(density) { 7.dp.toPx() }

        // ── Scrim with punched-out spotlight ─────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val breathing = if (settings.spotlightBreathing) spotlightPulseProgress else 0f
            val pulse = spotlightPulse(breathing, spotlightInflatePx)
            val hole = spotlightPath(spotlight.inflate(pulse.inflatePx), shape, cornerRadiusPx + pulse.inflatePx)
            val scrim = Path().apply { addRect(size.toRect()) }
            drawPath(Path().apply { op(scrim, hole, PathOperation.Difference) }, color = scrimColor)
            drawPath(hole, color = highlightColor, alpha = pulse.fillAlpha)
            drawPath(hole, color = highlightColor, alpha = pulse.outlineAlpha, style = Stroke(width = outlineStrokePx))

            // Draw a pulsing ring around the hole to signal "tap exactly here".
            if (settings.showTapRing && step.tapHint == TapHint.EXACT) {
                // wrongTapBoost decays from 1 (just tapped wrong) to 0 (settled) over ~400ms.
                val wrongTapBoost = 1f - wrongTap.targetPulse.value
                val inflate = ringInflatePx * ringProgress + ringInflatePx * 1.5f * wrongTapBoost
                val alpha = (0.8f * (1f - ringProgress)) + (0.6f * wrongTapBoost)
                drawPath(
                    path = spotlightPath(spotlight.inflate(inflate), shape, cornerRadiusPx + inflate),
                    color = highlightColor,
                    alpha = alpha.coerceIn(0f, 1f),
                    style = Stroke(width = ringStrokePx + ringStrokePx * wrongTapBoost),
                )
            }

            // Wrong-tap indicator: an expanding circle with a cross where the user tapped
            val indicatorProgress = wrongTap.indicator.value
            val tapPosition = wrongTap.position
            if (indicatorProgress < 1f && tapPosition.isSpecified) {
                val alpha = 1f - indicatorProgress
                val radius = indicatorStartPx + (indicatorEndPx - indicatorStartPx) * indicatorProgress
                drawCircle(feedbackColor, radius = radius, center = tapPosition, alpha = alpha, style = Stroke(outlineStrokePx))
                drawLine(feedbackColor, tapPosition + Offset(-crossPx, -crossPx), tapPosition + Offset(crossPx, crossPx), outlineStrokePx, StrokeCap.Round, alpha = alpha)
                drawLine(feedbackColor, tapPosition + Offset(-crossPx, crossPx), tapPosition + Offset(crossPx, -crossPx), outlineStrokePx, StrokeCap.Round, alpha = alpha)
            }
        }

        if (passThrough) TapCatchersAround(spotlight, screenWidthPx, screenHeightPx, density, onTap)

        // Screen readers: the highlighted area as a button (semantics only, so pass-through taps still reach the app)
        Box(
            Modifier
                .rectPlacement(spotlight, density)
                .semantics {
                    role = Role.Button
                    contentDescription = strings.highlightedTarget
                    onClick(label = strings.highlightedTarget) { controller.next(); true }
                }
        )

        // ── Info bubble ──────────────────────────────────────────────
        val showHint = bubbleStyle.showTapHint && step.tapHint != null
        if (step.content != null || showHint || bubbleStyle.showProgress) {

            // Horizontal: keep within [edgePadding, screenWidth - bubbleWidth - edgePadding]
            val clampedX = spotlight.left
                .coerceIn(
                    edgePaddingPx,
                    (screenWidthPx - bubbleSize.width - edgePaddingPx).coerceAtLeast(edgePaddingPx)
                )
                .toInt()

            // Vertical: prefer below the spotlight; flip above if it would clip the bottom
            val belowY = spotlight.bottom + gapPx
            val aboveY = spotlight.top - gapPx - bubbleSize.height

            val clampedY = if (belowY + bubbleSize.height <= screenHeightPx) {
                belowY.toInt()
            } else {
                // Show above; clamp so it never goes above the top edge either
                aboveY.coerceAtLeast(edgePaddingPx).toInt()
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(clampedX, clampedY) }
                    .graphicsLayer { translationX = wrongTap.shake.value }
                    .widthIn(max = bubbleStyle.maxWidth)
                    .onGloballyPositioned { bubbleSize = it.size }
                    .then(
                        // With a free hole the parent has no tap handling, so the bubble needs its own
                        if (passThrough) {
                            Modifier.pointerInput(step, clampedX, clampedY) {
                                detectTapGestures { onTap(it + Offset(clampedX.toFloat(), clampedY.toFloat())) }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                TourCard(
                    style = bubbleStyle,
                    spacing = 4.dp,
                    modifier = Modifier.tourStepSemantics(controller, strings),
                ) {
                    StepProgress(controller, strings, bubbleStyle)
                    step.content?.invoke(this)
                    if (showHint) {
                        TapHintRow(
                            hint = step.tapHint,
                            strings = strings,
                            style = bubbleStyle,
                            hasContentAbove = step.content != null,
                            flash = { wrongTap.flash.value },
                            flashColor = feedbackColor,
                        )
                    }
                }
            }
        }

        // Guide hand: moves from the last wrong tap to the target
        if (feedback.showGuideAfter > 0 && wrongTap.guideStart.isSpecified) {
            val handSize = 40.dp
            val handSizePx = with(density) { handSize.toPx() }
            Icon(
                imageVector = Icons.Filled.TouchApp,
                contentDescription = null,
                tint = highlightColor,
                modifier = Modifier
                    .offset {
                        val position = lerp(wrongTap.guideStart, spotlight.center, wrongTap.guide.value)
                        // The finger tip sits at the top center of the icon
                        IntOffset((position.x - handSizePx / 2f).safeRoundToInt(), (position.y - handSizePx / 6f).safeRoundToInt())
                    }
                    .size(handSize)
                    .graphicsLayer {
                        val progress = wrongTap.guide.value
                        alpha = if (progress >= 1f) 0f else 1f - ((progress - 0.8f) / 0.2f).coerceIn(0f, 1f)
                    },
            )
        }
    }
}

/** Four tap areas around [hole], so the hole itself passes taps to the app below. */
@Composable
private fun TapCatchersAround(
    hole: Rect,
    screenWidthPx: Float,
    screenHeightPx: Float,
    density: Density,
    onTap: (Offset) -> Unit,
) {
    val top = hole.top.coerceIn(0f, screenHeightPx)
    val bottom = hole.bottom.coerceIn(0f, screenHeightPx)
    val left = hole.left.coerceIn(0f, screenWidthPx)
    val right = hole.right.coerceIn(0f, screenWidthPx)
    val areas = listOf(
        Rect(0f, 0f, screenWidthPx, top),
        Rect(0f, bottom, screenWidthPx, screenHeightPx),
        Rect(0f, top, left, bottom),
        Rect(right, top, screenWidthPx, bottom),
    )
    for (area in areas) {
        if (area.width <= 0f || area.height <= 0f) continue
        Box(
            Modifier
                .rectPlacement(area, density)
                .pointerInput(area) { detectTapGestures { onTap(it + area.topLeft) } }
        )
    }
}

/** Places a box exactly over [rect] (root coordinates, the overlay fills the root). */
private fun Modifier.rectPlacement(rect: Rect, density: Density): Modifier =
    offset { IntOffset(rect.left.safeRoundToInt(), rect.top.safeRoundToInt()) }
        .size(
            with(density) {
                DpSize(rect.width.finiteOrZero().coerceAtLeast(0f).toDp(), rect.height.finiteOrZero().coerceAtLeast(0f).toDp())
            }
        )

/** roundToInt throws for NaN; a broken position must not crash the app. */
private fun Float.safeRoundToInt(): Int = if (isNaN()) 0 else roundToInt()

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

/** Announces each new step to screen readers and offers continue / back / skip as actions. */
private fun Modifier.tourStepSemantics(controller: TapTargetController, strings: TourStrings): Modifier =
    semantics {
        liveRegion = LiveRegionMode.Polite
        customActions = buildList {
            add(CustomAccessibilityAction(strings.continueButton) { controller.next(); true })
            if (controller.currentIndex > 0) {
                add(CustomAccessibilityAction(strings.previousStep) { controller.previous(); true })
            }
            add(CustomAccessibilityAction(strings.skipTour) { controller.skip(); true })
        }
    }

/** Surface of bubbles, cards and the free-roam bar, styled by [BubbleStyle]. */
@Composable
private fun TourCard(
    style: BubbleStyle,
    spacing: Dp,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val containerColor = style.containerColor.takeOrElse { MaterialTheme.colorScheme.surface }
    val contentColor = style.contentColor.takeOrElse { contentColorFor(containerColor) }
    Surface(
        modifier = modifier,
        shape = style.shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = style.shadowElevation,
    ) {
        Column(
            modifier = Modifier.padding(style.contentPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

@Composable
private fun StepProgress(controller: TapTargetController, strings: TourStrings, style: BubbleStyle) {
    if (!style.showProgress) return
    // The app's formatter must not crash the tour
    val text = runCatching { strings.stepProgress(controller.currentIndex + 1, controller.stepCount) }.getOrDefault("")
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.7f),
    )
}

/**
 * Renders a step with no spotlight — full scrim, no punched hole, bubble centered on screen.
 */
@Composable
private fun CenteredTourStep(controller: TapTargetController, step: TourStep, strings: TourStrings) {
    val settings = controller.tourSettings
    val bubbleStyle = settings.bubble
    val scrimColor = step.backgroundColor.takeOrElse { settings.scrimColor }
    val showHint = bubbleStyle.showTapHint && step.tapHint != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(step) {
                detectTapGestures { controller.next() }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = scrimColor)
        }

        if (step.content != null || showHint || bubbleStyle.showProgress) {
            TourCard(
                style = bubbleStyle,
                spacing = 8.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .widthIn(max = bubbleStyle.centeredMaxWidth)
                    .tourStepSemantics(controller, strings),
            ) {
                StepProgress(controller, strings, bubbleStyle)
                step.content?.invoke(this)
                if (showHint) {
                    TapHintRow(
                        hint = step.tapHint,
                        strings = strings,
                        style = bubbleStyle,
                        hasContentAbove = step.content != null,
                        flash = { 0f },
                        flashColor = Color.Unspecified,
                    )
                }
            }
        }
    }
}

/**
 * Small icon + label row explaining whether this step expects a tap on the highlighted
 * target ([TapHint.EXACT]) or a tap anywhere on screen ([TapHint.ANYWHERE]).
 *
 * [flash] (0..1) tints the row in [flashColor] and grows it a little after a wrong tap. It is read
 * in the draw phase only, so the animation does not recompose.
 */
@Composable
private fun ColumnScope.TapHintRow(
    hint: TapHint?,
    strings: TourStrings,
    style: BubbleStyle,
    hasContentAbove: Boolean,
    flash: () -> Float,
    flashColor: Color,
) {
    if (hint == null) return
    if (hasContentAbove) {
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))
    }
    Row(
        modifier = Modifier
            .graphicsLayer {
                val grow = 1f + 0.06f * flash()
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(0f, 0.5f)
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                val amount = flash()
                if (amount > 0f && flashColor.isSpecified) {
                    drawRect(flashColor, alpha = amount, blendMode = BlendMode.SrcAtop)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = style.tapHintIcon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (hint == TapHint.EXACT) strings.tapHighlighted else strings.tapAnywhere,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Non-blocking hint bar for free-roam steps. Anchored at [position]; everything outside
 * the bar itself remains fully interactive so the user can explore the real app. Only
 * the Continue button advances — there's no tap-anywhere-to-advance here, since a stray
 * tap during free exploration shouldn't accidentally skip the step.
 */
@Composable
private fun FreeRoamTourBar(
    controller: TapTargetController,
    step: TourStep,
    position: FreeRoamBarPosition,
    strings: TourStrings,
    continueText: String,
    continueButton: (@Composable (text: String, onContinue: () -> Unit) -> Unit)?,
) {
    val bubbleStyle = controller.tourSettings.bubble
    val alignment = when (position) {
        FreeRoamBarPosition.Top    -> Alignment.TopCenter
        FreeRoamBarPosition.Center -> Alignment.Center
        FreeRoamBarPosition.Bottom -> Alignment.BottomCenter
    }
    // Top/Bottom read as a spanning banner; Center reads as a compact card.
    val widthModifier = if (position == FreeRoamBarPosition.Center) {
        Modifier.widthIn(max = 320.dp)
    } else {
        Modifier.fillMaxWidth()
    }
    val onContinue = { controller.next() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        contentAlignment = alignment,
    ) {
        TourCard(
            style = bubbleStyle,
            spacing = 4.dp,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = widthModifier.tourStepSemantics(controller, strings),
        ) {
            StepProgress(controller, strings, bubbleStyle)
            step.content?.invoke(this)
            Spacer(Modifier.height(8.dp))
            if (continueButton != null) {
                continueButton(continueText, onContinue)
            } else {
                Button(onClick = onContinue) {
                    Text(continueText)
                }
            }
        }
    }
}

/** One frame of the breathing spotlight: how far it grows, how strong its fill and outline are. */
internal data class SpotlightPulse(val inflatePx: Float, val fillAlpha: Float, val outlineAlpha: Float)

/** [pulse] runs 0 (rest) to 1 (fully grown). */
internal fun spotlightPulse(pulse: Float, maxInflatePx: Float): SpotlightPulse {
    val p = pulse.coerceIn(0f, 1f)
    return SpotlightPulse(
        inflatePx = maxInflatePx * p,
        fillAlpha = 0.18f * p,
        outlineAlpha = 0.65f + 0.35f * p,
    )
}
