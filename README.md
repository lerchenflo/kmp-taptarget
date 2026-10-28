# kmp-taptarget

[![JitPack](https://jitpack.io/v/lerchenflo/kmp-taptarget.svg)](https://jitpack.io/#lerchenflo/kmp-taptarget)

Spotlight onboarding tours for **Compose Multiplatform** (Android, iOS, desktop).

Highlight any composable, explain it in a bubble with your own content, and walk the user through
your app across several screens and tabs. The library works with any navigation library and stores
nothing by itself.

```kotlin
TapTargetHost(controller = rememberTapTargetController(tour = myTour)) {
    MyApp()   // add Modifier.tapTarget("id") to the composables the tour points at
}
```

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Concepts](#concepts)
- [Step types](#step-types)
- [Custom bubble content](#custom-bubble-content)
- [Navigation](#navigation)
- [Starting, restarting and saving progress](#starting-restarting-and-saving-progress)
- [Localization](#localization)
- [Skip button](#skip-button)
- [Wrong-tap feedback](#wrong-tap-feedback)
- [Letting the tap reach the target](#letting-the-tap-reach-the-target)
- [Back button, keyboard and free-roam steps](#back-button-keyboard-and-free-roam-steps)
- [Accessibility](#accessibility)
- [Callbacks and errors](#callbacks-and-errors)
- [Settings](#settings)
- [API reference](#api-reference)
- [Behaviour and limitations](#behaviour-and-limitations)
- [Troubleshooting](#troubleshooting)
- [Demo app](#demo-app)
- [Testing library changes in your app before a release](#testing-library-changes-in-your-app-before-a-release)
- [Releasing a new version](#releasing-a-new-version)
- [Guide for AI coding assistants](#guide-for-ai-coding-assistants)
- [Changelog](#changelog)
- [License](#license)

## Features

- **Spotlight steps**: dim the screen, cut a rounded hole around a target composable and show an
  info bubble next to it. The bubble goes below the target, or above it when there is no room. The
  spotlight "breathes" and has an outline in the theme's primary color, so the eye finds it at once.
- **Info steps**: a centered card without a spotlight, for welcome and goodbye screens.
- **Free-roam steps**: a non-blocking hint bar. The user can use the real app until they press
  Continue.
- **Any bubble content**: every bubble is a `ColumnScope` slot, so it can hold icons, rows, images
  or anything else. `TourTitle` and `TourDescription` give you the default text styles.
- **Tours across screens and tabs**: every step can name a route. The controller navigates there and
  waits for the target to appear. Works with Navigation 3, Jetpack Navigation, Voyager, Decompose or
  your own back stack.
- **Tap modes**: a step can require a tap exactly on the target, or accept a tap anywhere.
- **Clear wrong-tap feedback**: a tap next to the target shows a mark where the user tapped,
  shakes the bubble, flashes the hint, vibrates and, after a few tries, a hand shows where to tap.
- **Pass-through taps**: optionally the tap also reaches the real button, so the app reacts as usual.
- **Back button and keyboard**: system back goes one step back (or blocks, or skips); on desktop
  Enter, the arrow keys and Escape work. Free-roam steps can keep the user on their screen.
- **Accessible**: screen readers read every step, offer continue / back / skip actions and see
  only the tour while it blocks the app.
- **Crash-safe**: an exception in one of your callbacks is reported to `onError`, never thrown
  into your app.
- **Customisable**: colors, shapes, sizes, texts, buttons, animations and behaviour, globally in
  `TourSettings` or per step, also while the tour runs.
- **Auto-scroll**: a target inside a scrollable container is scrolled to the middle of the screen.
- **Follows moving targets**: the spotlight tracks its target while it scrolls, animates or
  changes its layout.
- **Survives recreation**: `rememberTapTargetController` keeps the current step across Android
  activity recreation (rotation) and process death.
- **Skip button**: an optional composable slot, so you choose the text, language and style.
- **Localizable**: every built-in text can be replaced, and the tour DSL accepts `StringResource`
  from Compose resources directly.
- **Robust**: a step whose target never appears (for example a button hidden by state) is skipped
  after 2 seconds instead of blocking the tour.
- **No setup in your screens**: `Modifier.tapTarget(id)` is the only thing a screen needs. Without
  a `TapTargetHost` above it, the modifier does nothing, so previews and tests are unaffected.

## Requirements

| Item                  | Version / value                                                     |
|-----------------------|---------------------------------------------------------------------|
| Kotlin                | 2.4.20 or newer                                                     |
| Compose Multiplatform | 1.12.1 or newer                                                     |
| material3             | `org.jetbrains.compose.material3:material3` 1.12.0-alpha03 or newer |
| Android               | minSdk 24, compileSdk 37                                            |
| iOS                   | `iosArm64`, `iosSimulatorArm64` (no `iosX64`)                       |
| Desktop               | JVM 11 or newer                                                     |

The library is built against the lowest versions above on purpose. Gradle always picks the highest
version of a dependency in your app, so the library never upgrades Compose or material3 in your
app behind your back.

## Installation

The library is published on [JitPack](https://jitpack.io/#lerchenflo/kmp-taptarget). JitPack builds
it straight from a git tag of this repository, so there is no Maven Central account or signing.

**1. Add the JitPack repository** in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

**2. Add the version** to `gradle/libs.versions.toml`. The latest version is `1.0.4`, see the
[changelog](#changelog) and the [tags page](https://github.com/lerchenflo/kmp-taptarget/tags):

```toml
[versions]
kmpTaptarget = "1.0.4"

[libraries]
kmp-taptarget = { module = "com.github.lerchenflo.kmp-taptarget:kmp-taptarget", version.ref = "kmpTaptarget" }
```

Use exactly this coordinate. JitPack's short form `com.github.lerchenflo:kmp-taptarget` pulls the
Android, iOS and JVM artifacts all at once and breaks Kotlin Multiplatform dependency resolution
("No matching variant ... kmp-taptarget-android").

**3. Add the dependency** to the module that holds your UI (for example `composeApp` or `shared`):

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kmp.taptarget)
        }
    }
}
```

All public API lives in the package `io.github.lerchenflo.taptarget`.

## Quick start

Three steps: define the tour, put a `TapTargetHost` at the root, mark the targets.

### 1. Define the tour

```kotlin
import io.github.lerchenflo.taptarget.FreeRoamBarPosition
import io.github.lerchenflo.taptarget.TourDescription
import io.github.lerchenflo.taptarget.TourTitle
import io.github.lerchenflo.taptarget.tapTargetTour

val onboardingTour = tapTargetTour {
    infoStep(route = Route.Home) {
        TourTitle("Welcome")
        TourDescription("A short tour through the app.")
    }

    tapStep(id = "new_entry_button", route = Route.Home) {
        TourTitle("New entry")
        TourDescription("Create a new entry here.")
    }

    tapStep(id = "settings_tab", route = Route.Home, requireExactTap = false) {
        TourTitle("Settings")
        TourDescription("Everything else lives here.")
    }

    freeRoamStep(route = Route.Settings, position = FreeRoamBarPosition.Bottom) {
        TourTitle("Look around")
        TourDescription("Try the settings, then press Continue.")
    }
}
```

### 2. Create the controller and wrap your app in `TapTargetHost`

```kotlin
import io.github.lerchenflo.taptarget.TapTargetHost
import io.github.lerchenflo.taptarget.TourSkipButton
import io.github.lerchenflo.taptarget.rememberTapTargetController

@Composable
fun App(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val tourController = rememberTapTargetController(
        tour = onboardingTour,
        onNavigateToRoute = { route -> navigator.navigate(route as Route) },
        currentRoute = { backStack.lastOrNull() },
        onFinished = { viewModel.onAction(AppAction.OnboardingFinished) },  // persist "seen" here
    )

    LaunchedEffect(state.showOnboarding) {
        if (state.showOnboarding && !tourController.isActive) tourController.start()
    }

    TapTargetHost(
        controller = tourController,
        skipButton = { onSkip -> TourSkipButton(text = "Skip tour", onSkip = onSkip) },
    ) {
        AppContent()
    }
}
```

`TapTargetHost` gives the controller to every composable inside it and draws the tour overlay on
top. You do not need a `CompositionLocalProvider` or a `Box` of your own.

### 3. Mark the targets

Add `Modifier.tapTarget(id)` to every composable a `tapStep` points at. The id must match the step
id.

```kotlin
import io.github.lerchenflo.taptarget.tapTarget

FloatingActionButton(
    onClick = { /* ... */ },
    modifier = Modifier.tapTarget("new_entry_button"),
) {
    Icon(Icons.Filled.Add, contentDescription = "New entry")
}
```

That is all. Keep the ids in one place (an `object` with `const val`s), so the tour and the screens
cannot drift apart.

## Concepts

| Term           | Meaning                                                                                        |
|----------------|------------------------------------------------------------------------------------------------|
| **Tour**       | An ordered list of steps, built with `tapTargetTour { ... }`. Immutable.                       |
| **Step**       | One stop of the tour: `tapStep`, `infoStep` or `freeRoamStep`.                                 |
| **Target**     | A composable marked with `Modifier.tapTarget(id)`. A `tapStep` with the same id spotlights it. |
| **Route**      | Any object that identifies a screen in your navigation. The library only compares it.          |
| **Controller** | `TapTargetController`: holds the current step, navigates, collects target positions.           |
| **Host**       | `TapTargetHost`: root composable that provides the controller and draws the overlay.           |

## Step types

| Builder function | Spotlight | Blocks the app | Advances on                                                   |
|------------------|-----------|----------------|---------------------------------------------------------------|
| `tapStep`        | yes       | yes            | a tap on the target, or anywhere if `requireExactTap = false` |
| `highlightStep`  | yes, calm, dashed, lightly dimmed | yes | "Got it" or a tap anywhere except on the target |
| `infoStep`       | no        | yes            | a tap anywhere                                                |
| `freeRoamStep`   | no        | no             | the Continue button in the hint bar                           |

Each builder function has two variants:

- **Slot variant**: a trailing `@Composable ColumnScope.() -> Unit` lambda for custom content.
- **Resource variant**: `title` / `description` as `StringResource`, for plain text steps.

```kotlin
// Slot variant
tapStep(id = "export") {
    TourTitle("Export")
    TourDescription("Save or share as image")
}

// Resource variant
tapStep(
    id = "export",
    title = Res.string.tour_export_title,
    description = Res.string.tour_export_description,
)
```

Parameters:

| Parameter            | On                    | Description                                                                   |
|----------------------|-----------------------|-------------------------------------------------------------------------------|
| `id`                 | `tapStep`             | Target id, matches `Modifier.tapTarget(id)`.                                  |
| `route`              | all                   | Screen the step lives on. The controller navigates there first. `null`: stay. |
| `requireExactTap`    | `tapStep`             | `true` (default): only a tap on the target advances. `false`: a tap anywhere. |
| `backgroundColor`    | `tapStep`, `infoStep` | Scrim color. Default: `DefaultTourBackgroundColor` (black, 65 % opacity).     |
| `position`           | `freeRoamStep`        | Hint bar position: `Top`, `Center` or `Bottom`.                               |
| `continueButtonText` | `freeRoamStep`        | Continue button label. Falls back to `TourStrings.continueButton`.            |

### Highlight steps

Use `highlightStep` for an element the user should know about but not press now (for example
"here you can create groups later"). The outline is calm and dashed, there is no pulse ring, the
cutout stays lightly dimmed, and the bubble shows an info icon and a "Got it" button. A tap on the
highlighted element does nothing (it never reaches the element); a tap anywhere else continues:

```kotlin
highlightStep(
    id = "create_group_button",
    title = Res.string.tour_create_group_title,
    description = Res.string.tour_create_group_description,
)
```

The button text comes from `TourStrings.gotIt` or the step's `buttonText`, the button itself can be
replaced with the `continueButton` slot of `TapTargetHost`, and `dashedHighlightOutline = false`
in `TourSettings` draws a solid outline instead. `highlightCutoutScrim` (default `0.6f`) sets how
dark the cutout is, as a fraction of the scrim: `0f` leaves it fully clear, `1f` makes it as dark as
the rest; `highlightCutoutColor` (for example `MaterialTheme.colorScheme.outline`) greys the
element out instead of darkening it, and `highlightOutlineWidth` (default `4.dp`) sets the outline.
`passTapToTarget` never applies to highlight steps.

## Custom bubble content

The bubble content runs inside a `Column` with a small vertical spacing. Use the provided text
composables for consistent styling, and add any other composables you need:

```kotlin
tapStep(id = "entry_card", requireExactTap = false) {
    TourTitle("Entries")
    TourDescription("Each entry can contain:")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Pause, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        TourDescription("Breaks")
    }
}
```

The spotlight bubble is at most 260 dp wide, the centered info card at most 280 dp.

## Navigation

The controller does not depend on a navigation library. Routes are typed as `Any`, and you pass
three functions to `rememberTapTargetController`:

| Parameter           | Purpose                                                                            |
|---------------------|------------------------------------------------------------------------------------|
| `onNavigateToRoute` | Navigate to the given route. Called when a step's route is not the visible screen. |
| `currentRoute`      | Return the route of the visible screen.                                            |
| `isMatchingRoute`   | Optional. Is the visible route the step's route? Default: compares the classes, so `Route.Detail(1)` and `Route.Detail(2)` count as the same screen. |

The functions may capture changing values: the controller always calls the latest ones.

A tap on a highlighted target advances the tour. It is **not** passed to the composable underneath.
To move the user to another screen after a step, give the next step a `route`.

### Nested navigation (tabs with their own back stacks)

Return the top of the visible tab's back stack from `currentRoute`, and let `onNavigateToRoute`
switch the tab when the route belongs to another one. Navigating to a tab's root should clear that
tab's stack instead of pushing a second copy. The demo app does exactly this, see
[`ExampleNavigation.kt`](example/src/commonMain/kotlin/io/github/lerchenflo/taptarget/example/ExampleNavigation.kt).

```kotlin
val tourController = rememberTapTargetController(
    tour = onboardingTour,
    onNavigateToRoute = { route -> (route as? Route)?.let(navigator::navigate) },
    currentRoute = { navigator.currentRoute },   // top of the visible tab's back stack
)
```

## Starting, restarting and saving progress

- `start()` resets the tour to the first step and shows it.
- The library stores nothing. Save a "tour seen" flag in `onFinished` (called after the last step
  and on skip) and decide in your app when to call `start()`.
- `rememberTapTargetController` saves the current step and the running state with
  `rememberSaveable`. After a rotation or process death on Android the tour continues where it was.
  **Do not call `start()` unconditionally in `LaunchedEffect(Unit)`**: it would reset a restored
  tour to step 1. Call it only when the "seen" flag says so, and only if `!controller.isActive`.

To restart the tour from a settings screen, read the controller from the composition. Any
composable inside `TapTargetHost` can do this:

```kotlin
val tourController = LocalTapTargetController.current
Button(onClick = { tourController?.start() }) { Text("Restart tour") }
```

## Localization

The overlay shows a few texts by itself: the tap hints and the Continue button. They default to
English. Replace them with `TourStrings`, resolved from Compose resources at the call site:

```kotlin
TapTargetHost(
    controller = tourController,
    strings = TourStrings(
        tapHighlighted = stringResource(Res.string.tour_hint_tap_highlighted),
        tapAnywhere = stringResource(Res.string.tour_hint_tap_anywhere),
        continueButton = stringResource(Res.string.tour_continue),
    ),
    skipButton = { onSkip ->
        TourSkipButton(text = stringResource(Res.string.tour_skip), onSkip = onSkip)
    },
) {
    AppContent()
}
```

Step texts use the `StringResource` variants of the builder functions, or `stringResource(...)`
inside a slot.

## Skip button

`skipButton` is a composable slot that receives an `onSkip` callback. Leave it `null` to hide the
button. `TourSkipButton` is a ready-made `FilledTonalButton`, but any composable works:

```kotlin
skipButton = { onSkip ->
    TextButton(onClick = onSkip) { Text("Not now") }
}
```

The button respects the safe drawing insets. It sits in the half of the screen opposite to the
spotlight or free-roam bar, so it never covers the target. Pressing it calls
`TapTargetController.skip()`, which also calls `onFinished`.

## Wrong-tap feedback

On a step with `requireExactTap = true` (the default), a tap next to the target does not
continue. The user gets feedback, which `TourSettings.wrongTapFeedback` configures:

```kotlin
TourSettings(
    wrongTapFeedback = WrongTapFeedback(
        tapIndicator = true,    // expanding circle with a cross where the user tapped
        shakeBubble = true,     // the bubble shakes
        flashHint = true,       // "Tap the highlighted area" flashes in the feedback color
        haptic = true,          // short "reject" vibration
        pulseTarget = true,     // the ring around the target pulses once
        showGuideAfter = 3,     // after 3 wrong taps a hand moves to the target (0 = never)
        color = Color.Unspecified, // Unspecified = MaterialTheme.colorScheme.error
    ),
)
```

`WrongTapFeedback.None` switches everything off. `onWrongTap` (see
[Callbacks and errors](#callbacks-and-errors)) reports every wrong tap.

## Letting the tap reach the target

By default a tap on the target only advances the tour; the composable underneath never gets it.
With `passTapToTarget = true` the tap reaches the real composable too, so a button runs its own
`onClick` (for example it navigates) and the tour advances at the same time:

```kotlin
tapStep(id = "appearance_entry", route = Route.Settings, passTapToTarget = true) {
    TourTitle("Appearance")
    TourDescription("Open the appearance settings.")
}
```

Set it for all steps with `TourSettings(passTapToTarget = true)`. Taps outside the target still
get the wrong-tap feedback.

## Back button, keyboard and free-roam steps

| Setting                 | Default        | Options                                                        |
|-------------------------|----------------|----------------------------------------------------------------|
| `backBehavior`          | `PreviousStep` | `PreviousStep`, `Block`, `SkipTour`, `PassThrough` (app handles it) |
| `freeRoamBackBehavior`  | `PassThrough`  | Same options, for free-roam steps.                             |
| `freeRoamLeaveBehavior` | `Allow`        | `Allow`, `ReturnToStepRoute`, `NextStep`, `SkipTour`           |
| `keyboardNavigation`    | `true`         | Enter / Space / Right arrow: next, Left arrow: back, Escape: skip |

`freeRoamLeaveBehavior` covers every other way of leaving a free-roam step's screen (the app's
back arrow, a tab, a link). It needs a `route` on the step: the library checks `currentRoute`
about every 200 ms and compares it with `isMatchingRoute`. To keep the user on the screen of a
free-roam step:

```kotlin
TourSettings(
    freeRoamBackBehavior = TourBackBehavior.Block,
    freeRoamLeaveBehavior = FreeRoamLeaveBehavior.ReturnToStepRoute,
)
```

The controller also has `previous()` and `goTo(index)` for your own buttons.

Back handling uses `NavigationBackHandler` from `androidx.navigationevent`. If the host provides
no `NavigationEventDispatcherOwner`, back handling is off (no crash). A screen that registers its
own back handler after the tour started can take precedence.

## Accessibility

- Every step bubble is a live region, so TalkBack and VoiceOver read it when it appears.
- The bubble offers the actions "Continue", "Back" and "Skip tour" (texts from `TourStrings`).
- The highlighted area is a button for screen readers ("Highlighted element"); activating it
  continues the tour.
- While a blocking step shows, `TapTargetHost` hides the app from screen readers, so the focus
  cannot get lost behind the scrim. Turn this off with `hideAppFromScreenReader = false`.

## Callbacks and errors

```kotlin
rememberTapTargetController(
    tour = onboardingTour,
    onStepShown = { index, step -> analytics.log("tour_step", index) },
    onWrongTap = { index, position -> analytics.log("tour_wrong_tap", index) },
    onSkipped = { index -> analytics.log("tour_skipped", index) },
    onTargetMissing = { index, id -> log("Tour target $id not found") },
    onFinished = { viewModel.onAction(AppAction.OnboardingFinished) },  // after the last step and on skip
    onError = { error -> log(error) },
)
```

**Crash safety**: every callback, `onNavigateToRoute`, `currentRoute`, `isMatchingRoute` and
`beforeShow` runs inside a guard. An exception is passed to `onError` and the tour goes on (a step
whose navigation failed waits for its target and is skipped if it never appears). The library
also never crashes on detached or NaN target positions, unbounded layouts, missing haptics or a
missing back dispatcher. Your own composables inside the bubbles are not guarded: Compose cannot
catch exceptions thrown during composition.

## Settings

All fields have defaults. `rememberTapTargetController` applies a new `tourSettings` value on
every composition, so settings can change while the tour runs (the demo app does this).

```kotlin
TourSettings(
    iconPadding = 4.dp,                              // space between target bounds and spotlight edge
    cornerRadius = 16.dp,                            // corner radius of a RoundedRect spotlight
    spotlightShape = SpotlightShape.RoundedRect,     // or Pill, Oval, Circle (Circle falls back to Pill for wide or tall targets)
    scrimColor = DefaultTourBackgroundColor,         // a step's backgroundColor overrides it
    highlightColor = Color.Unspecified,              // outline, ring, guide hand; Unspecified = primary
    spotlightBreathing = true,                       // the spotlight slowly grows and shrinks
    showTapRing = true,                              // pulsing ring on exact-tap steps
    dashedHighlightOutline = true,                   // highlightStep: dashed instead of solid outline
    highlightCutoutScrim = 0.6f,                     // highlightStep: dimming inside the cutout (0 = clear, 1 = like the scrim)
    highlightCutoutColor = Color.Unspecified,        // highlightStep: cutout color, Unspecified = scrim; a gray greys it out
    outlineWidth = 3.dp,                             // spotlight outline
    highlightOutlineWidth = 4.dp,                    // highlightStep outline
    skipMissingTargets = true,                       // skip steps whose target does not appear
    targetTimeout = 2.seconds,                       // how long a step waits for its target
    autoScroll = true,                               // scroll targets to the middle of the screen
    passTapToTarget = false,                         // see "Letting the tap reach the target"
    wrongTapFeedback = WrongTapFeedback(),           // see "Wrong-tap feedback"
    backBehavior = TourBackBehavior.PreviousStep,    // see "Back button, keyboard and free-roam steps"
    freeRoamBackBehavior = TourBackBehavior.PassThrough,
    freeRoamLeaveBehavior = FreeRoamLeaveBehavior.Allow,
    keyboardNavigation = true,
    hideAppFromScreenReader = true,
    bubble = BubbleStyle(
        maxWidth = 260.dp,                           // bubble next to a spotlight
        centeredMaxWidth = 280.dp,                   // card of info steps
        shape = RoundedCornerShape(12.dp),
        containerColor = Color.Unspecified,          // Unspecified = surface
        contentColor = Color.Unspecified,            // Unspecified = contentColorFor(container)
        shadowElevation = 4.dp,
        contentPadding = PaddingValues(12.dp),
        gap = 16.dp,                                 // distance between spotlight and bubble
        showTapHint = true,                          // "Tap the highlighted area" row
        tapHintIcon = Icons.Filled.TouchApp,
        showProgress = false,                        // "2 / 9", text from TourStrings.stepProgress
    ),
)
```

Per step, `tapStep` accepts `passTapToTarget`, `spotlightShape`, `backgroundColor` and
`beforeShow`, which override the settings for that step.

The free-roam Continue button can be replaced with the `continueButton` slot of `TapTargetHost`:

```kotlin
TapTargetHost(
    controller = tourController,
    continueButton = { text, onContinue -> OutlinedButton(onClick = onContinue) { Text(text) } },
) { AppContent() }
```

## API reference

| Symbol                              | Kind               | Description                                                           |
|-------------------------------------|--------------------|-----------------------------------------------------------------------|
| `tapTargetTour { }`                 | function           | Builds a `TapTargetTour` with the `TourBuilder` DSL.                  |
| `TourBuilder`                       | class              | DSL receiver with `tapStep`, `infoStep`, `freeRoamStep`.              |
| `TapTargetTour`                     | class              | Immutable list of `TourStep`s.                                        |
| `TourStep`                          | class              | One step. Usually created by the DSL, not by hand.                    |
| `FreeRoamBarPosition`               | enum               | `Top`, `Center`, `Bottom`.                                            |
| `DefaultTourBackgroundColor`        | `Color`            | Default scrim color.                                                  |
| `rememberTapTargetController`       | `@Composable` fun  | Creates and remembers a controller; keeps progress across recreation. |
| `TapTargetController`               | class              | Tour state and navigation. Members below.                             |
| `TapTargetController.start()`       | function           | Resets to the first step and starts the tour.                         |
| `TapTargetController.next()`        | function           | Goes to the next step; finishes the tour after the last one.          |
| `TapTargetController.skip()`        | function           | Ends the tour at once and calls `onFinished`.                         |
| `TapTargetController.isActive`      | `Boolean`          | `true` while the tour runs.                                           |
| `TapTargetController.currentIndex`  | `Int`              | Index of the current step.                                            |
| `TapTargetController.currentStep`   | `TourStep?`        | The current step, or `null` when the tour does not run.               |
| `TapTargetController.previous()`    | function           | Goes back one step.                                                   |
| `TapTargetController.goTo(index)`   | function           | Jumps to a step and starts the tour if needed.                        |
| `TapTargetController.stepCount`     | `Int`              | Number of steps.                                                      |
| `TapTargetController.isBlocking`    | `Boolean`          | `true` while a step shows that blocks the app (not free-roam).        |
| `TapTargetController.tourSettings`  | `TourSettings`     | Current settings, writable at runtime.                                |
| `TapTargetHost`                     | `@Composable` fun  | Root of the tour: provides the controller and draws the overlay.      |
| `Modifier.tapTarget(id)`            | modifier           | Marks a composable as the target of the `tapStep` with the same id.   |
| `LocalTapTargetController`          | `CompositionLocal` | The controller of the surrounding `TapTargetHost`, or `null`.         |
| `TapTargetOverlay`                  | `@Composable` fun  | The overlay alone. Only needed if you build the root layout yourself. |
| `TourTitle`, `TourDescription`      | `@Composable` fun  | Default text styles for bubble content.                               |
| `TourSkipButton`                    | `@Composable` fun  | Ready-made skip button.                                               |
| `TourStrings`                       | data class         | Built-in overlay texts.                                               |
| `TourSettings`                      | data class         | Behaviour and look of the tour, see [Settings](#settings).            |
| `WrongTapFeedback`                  | data class         | Feedback for taps next to the target.                                 |
| `BubbleStyle`                       | data class         | Look of bubbles, cards and the free-roam bar.                         |
| `SpotlightShape`                    | enum               | `RoundedRect`, `Pill`, `Oval`, `Circle` (square-ish targets only, else `Pill`). |
| `TourBackBehavior`                  | enum               | `PreviousStep`, `Block`, `SkipTour`, `PassThrough`.                   |
| `FreeRoamLeaveBehavior`             | enum               | `Allow`, `ReturnToStepRoute`, `NextStep`, `SkipTour`.                 |

If you build the root layout yourself instead of using `TapTargetHost`, provide the controller with
`CompositionLocalProvider(LocalTapTargetController provides controller)` and put
`TapTargetOverlay(controller)` as the last child of a full-size `Box`.

## Behaviour and limitations

- **One composable per id**: an id must be registered by only one composable at a time. If a
  `HorizontalPager` keeps several pages composed that share a screen composable, apply `tapTarget`
  only on the page that should be highlighted.
- **Targets must be composed**: a target that is not in the composition is not found. The step is
  skipped after 2 seconds (or waits forever with `skipMissingTargets = false`).
  - Items of a `LazyColumn` / `LazyRow` that are far off screen are **not** composed. Scroll the
    list in the step's `beforeShow`, which runs after the navigation and before the step waits:
    ```kotlin
    tapStep(id = "item_25", beforeShow = { listState.animateScrollToItem(24) }) { ... }
    ```
  - Content of a normal `Column` with `verticalScroll` is always composed, so auto-scroll reaches it.
- **Auto-scroll is vertical only**: a target inside a horizontal pager never swipes the pager to
  another page.
- **Taps on a target are consumed**: a tap that advances a step does not reach the composable below.
- **Persistence is up to you**: see [Starting, restarting and saving progress](#starting-restarting-and-saving-progress).

## Troubleshooting

| Problem                                              | Cause and fix                                                                                       |
|------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| A step is skipped                                    | The target id was not found within 2 s. Check the id, check that the target is composed (see lazy lists above), and check that the step's `route` opens the right screen. |
| The overlay never shows                              | `start()` was not called, or the composable is not inside `TapTargetHost`.                          |
| The spotlight is in the wrong place                  | `TapTargetHost` does not fill the window. Do not put padding or insets on its `modifier`.           |
| The tour starts again after a rotation               | `start()` is called without a guard in a `LaunchedEffect`. See above.                               |
| iOS crash with an `IrLinkage` error                  | material3 does not match the Compose Multiplatform version of your app. Use the material3 version listed next to your Compose Multiplatform version in its release notes. |
| UI tests cannot find app nodes during a tour         | While a blocking step shows, `TapTargetHost` hides the app from the semantics tree (also test tags). Use `TourSettings(hideAppFromScreenReader = false)` in tests. |
| Sync fails: `Could not find kmp-taptarget-iosarm64-1.0.0.jar` | Version 1.0.0 is affected by JitPack's metadata rewrite. Use 1.0.1 or newer. |
| `No matching variant of ...:kmp-taptarget-android` | The short coordinate `com.github.lerchenflo:kmp-taptarget` was used. Use `com.github.lerchenflo.kmp-taptarget:kmp-taptarget`. |
| `Could not find com.github.lerchenflo.kmp-taptarget:kmp-taptarget` | The JitPack repository is missing in `settings.gradle.kts`, or the version tag does not exist. Open `https://jitpack.io/#lerchenflo/kmp-taptarget` to see the builds. |

## Demo app

The repository contains a demo app that uses the library source directly, so every change can be
tried out before a release.

| Module             | What it is                                                        | Run it                                    |
|--------------------|-------------------------------------------------------------------|-------------------------------------------|
| `:library`         | The library itself (the only published module).                   |                                           |
| `:example`         | KMP module with the shared demo app and the desktop entry point.  | `./gradlew :example:run`                  |
| `:example-android` | Android app that hosts the shared demo app.                       | `./gradlew :example-android:installDebug` |

(`:example-android` is a separate module because AGP 9 does not allow the Kotlin Multiplatform
plugin and the Android application plugin in the same module.)

The demo shows every feature in one tour:

1. Welcome info step.
2. Spotlight on the add button, exact tap required.
3. Highlight step on the first item of a scrollable list: calm dashed outline, "Got it" button.
4. An item that starts off screen: `beforeShow` scrolls the lazy list to it. Tap next to it to see
   the wrong-tap feedback, three times for the guide hand.
5. Nested navigation: the tour opens a detail screen inside the Home tab and auto-scrolls to a card
   at the bottom of a long page.
6. Custom bubble content with icons, pointing at the Settings tab.
7. Tab switch and pass-through tap: the tour moves to the Settings tab, and the tap on
   "Appearance" really opens it.
8. Free-roam step on a nested settings screen: toggle dark mode, then press Continue.
9. Restart button that reads `LocalTapTargetController`.
10. Goodbye info step, back on the Home tab.

Rotate the Android device during the tour: the current step survives. The Settings tab has
"Tour options" switches that change `TourSettings` while the tour runs (pass-through taps,
wrong-tap feedback, circle spotlight, step progress, back behaviour, keeping the user on the
free-roam screen). The callbacks are printed to the console.

The demo code is a good template for your own integration:

- [`TapTargetExampleApp.kt`](example/src/commonMain/kotlin/io/github/lerchenflo/taptarget/example/TapTargetExampleApp.kt): controller, host, start logic.
- [`ExampleTour.kt`](example/src/commonMain/kotlin/io/github/lerchenflo/taptarget/example/ExampleTour.kt): tour definition and target ids.
- [`ExampleNavigation.kt`](example/src/commonMain/kotlin/io/github/lerchenflo/taptarget/example/ExampleNavigation.kt): tabs with nested back stacks.
- [`ExampleScreens.kt`](example/src/commonMain/kotlin/io/github/lerchenflo/taptarget/example/ExampleScreens.kt): screens with `Modifier.tapTarget`.

## Testing library changes in your app before a release

Use a Gradle composite build. It replaces the JitPack dependency with the local library source, so
no release is needed. In the **app's** `settings.gradle.kts` (with this repository checked out next
to the app):

```kotlin
includeBuild("../kmp-taptarget") {
    dependencySubstitution {
        substitute(module("com.github.lerchenflo.kmp-taptarget:kmp-taptarget")).using(project(":library"))
    }
}
```

Sync Gradle, and the app builds against your local changes. Remove the block before you commit.

## Releasing a new version

1. Try the change in the demo app (and, if needed, in an app with the composite build above).
2. Commit and push to `main`.
3. Create a git tag with the new version and push it, for example:
   ```bash
   git tag 1.0.5
   git push origin 1.0.5
   ```
4. Open `https://jitpack.io/#lerchenflo/kmp-taptarget`, click **Get it** next to the tag, and wait
   for the build. JitPack also builds a tag the first time an app asks for it, but then that first
   Gradle sync waits for the build.
5. Add the version to the [changelog](#changelog) and update the version in the
   [installation](#installation) section.
6. Raise the version in the apps' `libs.versions.toml`.

JitPack rewrites the Gradle module metadata and drops the classifier from every file name
(`x-1.0.0-metadata.jar` becomes `x-1.0.0.jar`). `library/build.gradle.kts` therefore publishes the
iOS metadata jar and the iOS resources zip a second time without classifier. Without this, the
Android Studio sync of apps fails with `Could not find kmp-taptarget-iosarm64-<version>.jar`.
For the same reason the sources jars are not variants in the module metadata (the rewrite would
point them to the classes jar). They are published as plain Maven `-sources.jar` files, which the
IDE finds by classifier, so it shows the KDoc on hover. Keep both when changing the build.

How the JitPack build works: `jitpack.yml` tells JitPack to run
`./gradlew :library:publishToMavenLocal -Pversion=<tag>` with JDK 25 on Linux. The example apps are
not built. Kotlin cross-compiles the iOS libraries on Linux, so iOS works without a Mac. A tag that
was built cannot be rebuilt with different code: fix problems with a new tag. Any commit can be
tried without a tag by using its short hash as version, for example
`com.github.lerchenflo.kmp-taptarget:kmp-taptarget:a1b2c3d`.

## Guide for AI coding assistants

This section is a compact, rule-based summary for tools that add the library to an app.

**Integration checklist**

1. Add `maven("https://jitpack.io")` to `dependencyResolutionManagement.repositories` in
   `settings.gradle.kts`.
2. Add `com.github.lerchenflo.kmp-taptarget:kmp-taptarget:<version>` to the version catalog and to
   `commonMain.dependencies` of the UI module. Never hardcode the version in a build file.
3. Create one `object` with `const val` target ids.
4. Define the tour with `tapTargetTour { ... }` in its own file. Give every step that lives on a
   different screen than the step before it a `route`.
5. In the root composable:
   `val controller = rememberTapTargetController(tour, onNavigateToRoute, currentRoute, onFinished = ...)`.
6. Wrap the root content in `TapTargetHost(controller = controller) { ... }`. Use exactly one host
   per window.
7. Add `Modifier.tapTarget(TargetIds.X)` to each targeted composable.
8. Start the tour only when a persisted "seen" flag is false and `!controller.isActive`. Persist the
   flag in `onFinished`.

**Rules**

- Do not wrap content in `CompositionLocalProvider(LocalTapTargetController ...)` yourself:
  `TapTargetHost` does it.
- Do not create the controller with plain `remember { TapTargetController(...) }`: use
  `rememberTapTargetController`, so progress survives recreation.
- Do not call `start()` in `LaunchedEffect(Unit)` without a guard.
- A `tapStep` at a lazy list item that may be off screen needs a `beforeShow` that scrolls to it.
- Use `passTapToTarget = true` when the target's own action (e.g. navigation) should run.
- Wrap callbacks that may fail? Not needed: the library catches their exceptions; pass `onError`
  to log them.
- Do not register the same id on two composables that are on screen at the same time.
- Use `MaterialTheme.colorScheme` in bubble content. Do not hardcode colors.
- Put user-facing step texts in string resources. Use the `StringResource` builder variants or
  `stringResource(...)` in slots.
- Routes are compared by class by default. Pass `isMatchingRoute` if two screens share a class.

**Public API in one block**

```kotlin
// package io.github.lerchenflo.taptarget
fun tapTargetTour(builder: TourBuilder.() -> Unit): TapTargetTour
class TourBuilder {
    fun tapStep(id: String, route: Any? = null, backgroundColor: Color = DefaultTourBackgroundColor, requireExactTap: Boolean = true, content: @Composable ColumnScope.() -> Unit)
    fun tapStep(id: String, title: StringResource? = null, description: StringResource? = null, route: Any? = null, backgroundColor: Color = DefaultTourBackgroundColor, requireExactTap: Boolean = true)
    fun highlightStep(id: String, route: Any? = null, backgroundColor: Color = Color.Unspecified, spotlightShape: SpotlightShape? = null, buttonText: String? = null, beforeShow: (suspend () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)
    fun highlightStep(id: String, title: StringResource? = null, description: StringResource? = null, route: Any? = null, backgroundColor: Color = Color.Unspecified, buttonText: StringResource? = null, beforeShow: (suspend () -> Unit)? = null, spotlightShape: SpotlightShape? = null)
    fun infoStep(route: Any? = null, backgroundColor: Color = DefaultTourBackgroundColor, content: @Composable ColumnScope.() -> Unit)
    fun infoStep(title: StringResource? = null, description: StringResource? = null, route: Any? = null, backgroundColor: Color = DefaultTourBackgroundColor)
    fun freeRoamStep(route: Any? = null, position: FreeRoamBarPosition = FreeRoamBarPosition.Bottom, continueButtonText: String? = null, content: @Composable ColumnScope.() -> Unit)
    fun freeRoamStep(title: StringResource? = null, description: StringResource? = null, route: Any? = null, position: FreeRoamBarPosition = FreeRoamBarPosition.Bottom, continueButtonText: StringResource? = null)
}
// tapStep also takes: passTapToTarget: Boolean? = null, spotlightShape: SpotlightShape? = null, beforeShow: (suspend () -> Unit)? = null
// infoStep and freeRoamStep also take: beforeShow: (suspend () -> Unit)? = null
@Composable fun rememberTapTargetController(tour: TapTargetTour, onNavigateToRoute: suspend (Any) -> Unit = {}, currentRoute: () -> Any? = { null }, isMatchingRoute: (targetRoute: Any, activeRoute: Any) -> Boolean = { t, a -> t::class == a::class }, onFinished: () -> Unit = {}, tourSettings: TourSettings = TourSettings(), onStepShown: (index: Int, step: TourStep) -> Unit = { _, _ -> }, onSkipped: (index: Int) -> Unit = {}, onWrongTap: (index: Int, position: Offset) -> Unit = { _, _ -> }, onTargetMissing: (index: Int, id: String) -> Unit = { _, _ -> }, onError: (Throwable) -> Unit = {}): TapTargetController
class TapTargetController { fun start(); fun next(); fun previous(); fun goTo(index: Int); fun skip(); val isActive: Boolean; val isBlocking: Boolean; val currentIndex: Int; val stepCount: Int; val currentStep: TourStep?; var tourSettings: TourSettings }
@Composable fun TapTargetHost(controller: TapTargetController, modifier: Modifier = Modifier, strings: TourStrings = TourStrings(), skipButton: (@Composable (onSkip: () -> Unit) -> Unit)? = null, continueButton: (@Composable (text: String, onContinue: () -> Unit) -> Unit)? = null, content: @Composable BoxScope.() -> Unit)
fun Modifier.tapTarget(id: String): Modifier
val LocalTapTargetController: ProvidableCompositionLocal<TapTargetController?>
@Composable fun TourTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null)
@Composable fun TourDescription(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null)
@Composable fun TourSkipButton(text: String, onSkip: () -> Unit, modifier: Modifier = Modifier)
data class TourStrings(val tapHighlighted: String, val tapAnywhere: String, val continueButton: String, val skipTour: String, val previousStep: String, val highlightedTarget: String, val stepProgress: (current: Int, total: Int) -> String)
data class TourSettings(/* see the Settings section for every field and its default */)
data class WrongTapFeedback(val tapIndicator: Boolean = true, val shakeBubble: Boolean = true, val flashHint: Boolean = true, val haptic: Boolean = true, val pulseTarget: Boolean = true, val showGuideAfter: Int = 3, val color: Color = Color.Unspecified) { companion object { val None: WrongTapFeedback } }
data class BubbleStyle(/* see the Settings section */)
enum class SpotlightShape { RoundedRect, Circle, Pill, Oval }
enum class TourBackBehavior { PreviousStep, Block, SkipTour, PassThrough }
enum class FreeRoamLeaveBehavior { Allow, ReturnToStepRoute, NextStep, SkipTour }
enum class FreeRoamBarPosition { Top, Center, Bottom }
```

## Changelog

### 1.0.4
- Publishes the sources jars again, so the IDE shows the KDoc on hover instead of decompiled
  signatures (`= COMPILED_CODE`).
- No behaviour changes.

### 1.0.3
- KDoc for every public function, class and parameter. The IDE only shows it from 1.0.4 on,
  because 1.0.3 publishes no sources jars.
- No behaviour changes.

### 1.0.2
- New `highlightStep`: points at an element only to inform. Calm dashed outline, lightly dimmed
  cutout, info icon and a "Got it" button. A tap on the element does nothing, a tap anywhere else
  continues.
- New settings: `dashedHighlightOutline`, `highlightCutoutScrim`, `highlightCutoutColor`,
  `outlineWidth`, `highlightOutlineWidth`; new text `TourStrings.gotIt`.
- The default scrim is a bit darker (black, 65 % instead of 55 %).

### 1.0.1
- Fixes the Android Studio sync of Kotlin Multiplatform apps (`Could not find
  kmp-taptarget-iosarm64-1.0.0.jar`), caused by JitPack's module metadata rewrite.
- No sources jars are published anymore.

### 1.0.0
- First release: spotlight, info and free-roam steps, `TapTargetHost`,
  `rememberTapTargetController` (progress survives recreation), wrong-tap feedback, pass-through
  taps, back button and keyboard handling, accessibility, callbacks, crash safety, `beforeShow`
  for lazy lists, spotlight shapes, and `TourSettings` for the look and behaviour.
- Do not use it in Kotlin Multiplatform apps: the Android Studio sync fails, use 1.0.1 or newer.

## License

Apache License 2.0, see [LICENSE](LICENSE).
