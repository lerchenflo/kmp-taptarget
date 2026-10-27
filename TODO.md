# TODO

Open work on the library, top to bottom. Tick an item when it is done and committed.
Goal (user, 2026-10-27): implement every improvement below and expose as many options as possible,
so apps can customise the library without opening a pull request.

Rules for this work:
- Keep the public API backwards compatible where possible (new parameters get defaults that keep
  today's behaviour, unless stated otherwise below).
- Colors default to `Color.Unspecified` and fall back to `MaterialTheme.colorScheme` at draw time.
  Never hardcode colors except `DefaultTourBackgroundColor`.
- Animations read their values in the draw / layout phase (`graphicsLayer`, `drawWithContent`,
  `offset {}` lambdas), never in composition.
- Compile check after each step (desktop machine, compile tasks only):
  `./gradlew :library:compileKotlinJvm :library:compileAndroidMain :library:compileKotlinIosSimulatorArm64 :library:compileTestKotlinJvm :example:compileKotlinJvm :example-android:compileDebugKotlin`
- Commit and push to `main` after each finished step.

## 1. Settings model (`TapTargetSettings.kt`, new file)

- Note: `beforeShow` is not the last parameter of the `StringResource` overloads, otherwise a
  trailing lambda matches it and the slot overload becomes ambiguous.
- [x] Move `TourStrings` out of `TapTargetOverlay.kt` into the new file and add:
  `skipTour = "Skip tour"`, `previousStep = "Back"`, `highlightedTarget = "Highlighted element"`,
  `stepProgress: (current: Int, total: Int) -> String = { c, t -> "$c / $t" }`.
- [x] `TourSettings` gets these fields (old ones keep their names and defaults):
  - `iconPadding: Dp = 4.dp`, `cornerRadius: Dp = 16.dp` (existing)
  - `spotlightShape: SpotlightShape = SpotlightShape.RoundedRect` (enum `RoundedRect`, `Circle`)
  - `scrimColor: Color = DefaultTourBackgroundColor` (used when a step has no own color)
  - `highlightColor: Color = Color.Unspecified` (outline, ring, guide hand; fallback `primary`)
  - `spotlightBreathing: Boolean = true`, `showTapRing: Boolean = true`
  - `skipMissingTargets: Boolean = true` (existing), `targetTimeout: Duration = 2.seconds`
  - `autoScroll: Boolean = true`
  - `passTapToTarget: Boolean = false`
  - `wrongTapFeedback: WrongTapFeedback = WrongTapFeedback()`
  - `backBehavior: TourBackBehavior = TourBackBehavior.PreviousStep`
    (enum `PreviousStep`, `Block`, `SkipTour`, `PassThrough`)
  - `keyboardNavigation: Boolean = true`
  - `hideAppFromScreenReader: Boolean = true`
  - `bubble: BubbleStyle = BubbleStyle()`
- [x] `WrongTapFeedback(tapIndicator, shakeBubble, flashHint, haptic, pulseTarget: Boolean = true,
  showGuideAfter: Int = 3 (0 = never), color: Color = Color.Unspecified (fallback error))`
  plus `companion val None` with everything off.
- [x] `BubbleStyle(maxWidth = 260.dp, centeredMaxWidth = 280.dp, shape = RoundedCornerShape(12.dp),
  containerColor/contentColor = Color.Unspecified (surface / contentColorFor), shadowElevation = 4.dp,
  contentPadding = PaddingValues(12.dp), gap = 16.dp (target to bubble), showTapHint = true,
  tapHintIcon: ImageVector = Icons.Filled.TouchApp, showProgress = false)`.
- [x] `TapTargetController.tourSettings` becomes a public `var` backed by `mutableStateOf`, so apps
  (and the demo) can change settings at runtime. `rememberTapTargetController` writes the latest
  `tourSettings` into it on every composition (`SideEffect`).

## 2. Steps and controller

- [x] `TourStep` gets per-step overrides: `passTapToTarget: Boolean? = null`,
  `spotlightShape: SpotlightShape? = null`, `beforeShow: (suspend () -> Unit)? = null`.
  `backgroundColor` default becomes `Color.Unspecified` (falls back to `TourSettings.scrimColor`).
  Add the new parameters (with defaults) to every `tapStep` / `infoStep` / `freeRoamStep` overload.
- [x] Controller: `previous()`, `goTo(index)`, `stepCount`, `isBlocking` (active and not free-roam).
- [x] Controller callbacks (also on `rememberTapTargetController`, all through `rememberUpdatedState`):
  `onStepShown(index, step)`, `onSkipped(index)`, `onWrongTap(index, offset)`,
  `onTargetMissing(index, id)`. `onFinished` stays (called on finish and on skip).
- [x] `ensureCurrentStepVisible`: after navigating, run `step.beforeShow`, then wait
  `tourSettings.targetTimeout`.
- [x] Missing target while going backwards (after `previous()`) skips backwards, not forwards
  (remember the direction of the last move).
- [x] Internal `onTargetTapped(id)`: advances only when the tour runs, the current step has this id
  and the step lets the tap through.

## 3. Wrong-tap feedback (main problem: a tap next to the target gives no visible feedback)

All parts can be switched off one by one with `WrongTapFeedback`.

- [x] Tap indicator: an expanding, fading circle with a small cross at the tap position, in the
  feedback color.
- [x] Shake the bubble horizontally (keyframes, about 400 ms, `graphicsLayer { translationX }`).
- [x] Flash the tap hint row in the feedback color and scale it up a bit for about 1.2 s
  (offscreen layer + `drawWithContent` with `BlendMode.SrcAtop`, no recomposition per frame).
- [x] Haptic feedback `HapticFeedbackType.Reject` (no-op where the platform has none).
- [x] Keep the existing ring boost around the target (`pulseTarget`).
- [x] Guide hand: after `showGuideAfter` wrong taps in a row, animate the `TouchApp` icon from the
  last wrong tap to the target center.
- [x] Call `onWrongTap(index, offset)`.

## 4. Let the real tap through (`passTapToTarget`)

- [x] When on (global or per step), the overlay leaves the spotlight hole free: four pointer areas
  around the hole catch taps (wrong taps, or "tap anywhere"), the hole itself gets no pointer input,
  so the real composable below receives the tap.
- [x] `TapTargetNode` also implements `PointerInputModifierNode`: watches the `Final` pass without
  consuming, counts down + up inside its bounds within touch slop as a tap, then calls
  `controller.onTargetTapped(id)`.

## 5. System back and keyboard

- [x] Overlay uses `NavigationBackHandler` (navigationevent-compose, add to the library catalog)
  while a blocking step shows: `PreviousStep` (on the first step: do nothing), `Block`, `SkipTour`;
  `PassThrough` disables the handler. Free-roam steps never intercept back.
- [x] `keyboardNavigation`: the overlay takes focus during blocking steps; Enter / Space /
  Right arrow = next (also activates the target on exact steps), Left arrow = previous,
  Escape = skip.

## 6. Accessibility

- [x] Bubble: `liveRegion = Polite`, so screen readers read each new step; custom actions
  "continue", "back" and "skip" (labels from `TourStrings`).
- [x] A semantics-only node over the spotlight hole: `Role.Button`, description
  `strings.highlightedTarget`, `onClick` = next (no pointer input, so pass-through still works).
- [x] `TapTargetHost` hides the app content from screen readers (`clearAndSetSemantics {}`) while a
  blocking step shows, if `hideAppFromScreenReader`.

## 7. Overlay customisation slots

- [x] `TapTargetHost` / `TapTargetOverlay` get `continueButton: (@Composable (text: String, onContinue: () -> Unit) -> Unit)? = null`
  for the free-roam bar (default: `Button`).
- [x] Bubble and centered card use `BubbleStyle`; optional step progress text (`showProgress`).
- [x] `SpotlightShape.Circle`: circle around the target center (radius = half the longer side
  plus padding); hole, outline and ring follow the shape.

## 8. Lazy lists

- [x] Document `beforeShow` for off-screen lazy items, for example
  `tapStep(id = "item_25", beforeShow = { listState.scrollToItem(24) })`.
- [ ] Remove the matching P4 item from `SchneaggchatV3/plans/Todo.md` once this is released.

## 9. Demo app

- [x] Home list: hoist `LazyListState`, build the tour in `remember(listState)`, add a step for a
  far item (`beforeShow` scroll).
- [x] Settings tab gets a "Tour options" section with switches that change `tourSettings` live:
  pass tap through, wrong-tap feedback on/off, circle spotlight, show progress, back = previous
  step / block.
- [x] The "Appearance" step uses `passTapToTarget = true` (the real tap opens the screen).
- [x] Log the callbacks (`println`) to show they exist.

## 10. Tests (compile only unless the user asks to run them)

- [x] Controller: `previous()`, `goTo()`, `restoreState`, backwards skipping.
- [x] Wrong tap calls `onWrongTap` and does not advance; tap on target advances.
- [x] Pass-through: the target's own `onClick` runs and the tour advances.
- [x] `Modifier.tapTarget` unregisters when it leaves the composition.

## 11. Docs and release prep

- [x] README: new sections (wrong-tap feedback, pass-through, back and keyboard, accessibility,
  callbacks, lazy lists, runtime settings), update Settings, API reference, AI guide and the
  demo list.
- [x] Check KDoc of every public symbol.
- [ ] Release only after the user has checked the demo (user: JitPack is not important now).
  JitPack answered 502 for the test build of commit `51669f8`; retry when releasing.

## 12. Crash safety (user, 2026-10-27: "if something doesn't work I don't want a crash")

- [x] Every app callback (navigation, route checks, `beforeShow`, `onFinished`, analytics callbacks)
  runs through `safely {}`: exceptions go to the new `onError` callback, the tour goes on.
- [x] No back handler without a `NavigationEventDispatcherOwner` (no crash in custom hosts / tests).
- [x] NaN / infinite target bounds and unbounded constraints draw nothing; rounding is NaN-safe.
- [x] Haptics, the progress formatter and auto-scroll can fail without crashing.
- [x] Saved state is restored with safe casts.

## 13. Free-roam steps and navigation (user, 2026-10-27)

- [x] `freeRoamBackBehavior` (default `PassThrough`): system back during a free-roam step.
- [x] `freeRoamLeaveBehavior` (`Allow`, `ReturnToStepRoute`, `NextStep`, `SkipTour`): the overlay
  polls the route while a free-roam step with a `route` shows and reacts when the user leaves it.
- [x] Demo switch "Keep the user on the free-roam screen".

## 14. Spotlight shapes (user, 2026-10-27: circle broken on wide targets)

- [x] `Circle` now encloses the whole target and falls back to `Pill` when the target's sides differ
  by more than 1.25x. New shapes `Pill` (capsule) and `Oval` (ellipse through the corners).

## 15. Moving targets (user, 2026-10-27)

- [x] The spotlight follows a target that moves (layout offset, scroll, `graphicsLayer`
  translation). Covered by two tests in `TapTargetHostTest`; `:library:jvmTest` ran green
  (14 tests) on 2026-10-27, user asked to make sure it works.

## Open

- [ ] User checks the demo app on desktop and Android (`./gradlew :example:run`,
  `./gradlew :example-android:installDebug`).
- [ ] Then: first tag + JitPack build, then switch SchneaggchatV3 (see its `plans/Todo.md`) and the
  other apps (their TODO files).
