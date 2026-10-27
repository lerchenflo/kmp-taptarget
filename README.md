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
- [Settings](#settings)
- [API reference](#api-reference)
- [Behaviour and limitations](#behaviour-and-limitations)
- [Troubleshooting](#troubleshooting)
- [Demo app](#demo-app)
- [Testing library changes in your app before a release](#testing-library-changes-in-your-app-before-a-release)
- [Releasing a new version](#releasing-a-new-version)
- [Guide for AI coding assistants](#guide-for-ai-coding-assistants)
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
- **Tap modes**: a step can require a tap exactly on the target (with a pulsing ring and a
  wrong-tap pulse), or accept a tap anywhere.
- **Auto-scroll**: a target inside a scrollable container is scrolled to the middle of the screen.
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

**2. Add the version** to `gradle/libs.versions.toml`. Replace `<version>` with a tag from the
[tags page](https://github.com/lerchenflo/kmp-taptarget/tags):

```toml
[versions]
kmpTaptarget = "<version>"

[libraries]
kmp-taptarget = { module = "com.github.lerchenflo:kmp-taptarget", version.ref = "kmpTaptarget" }
```

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
| `backgroundColor`    | `tapStep`, `infoStep` | Scrim color. Default: `DefaultTourBackgroundColor` (black, 55 % opacity).     |
| `position`           | `freeRoamStep`        | Hint bar position: `Top`, `Center` or `Bottom`.                               |
| `continueButtonText` | `freeRoamStep`        | Continue button label. Falls back to `TourStrings.continueButton`.            |

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

## Settings

```kotlin
rememberTapTargetController(
    tour = onboardingTour,
    tourSettings = TourSettings(
        iconPadding = 4.dp,         // space between the target bounds and the spotlight edge
        cornerRadius = 16.dp,       // corner radius of the spotlight hole
        skipMissingTargets = true,  // skip steps whose target does not appear within 2 s
    ),
)
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
| `TapTargetHost`                     | `@Composable` fun  | Root of the tour: provides the controller and draws the overlay.      |
| `Modifier.tapTarget(id)`            | modifier           | Marks a composable as the target of the `tapStep` with the same id.   |
| `LocalTapTargetController`          | `CompositionLocal` | The controller of the surrounding `TapTargetHost`, or `null`.         |
| `TapTargetOverlay`                  | `@Composable` fun  | The overlay alone. Only needed if you build the root layout yourself. |
| `TourTitle`, `TourDescription`      | `@Composable` fun  | Default text styles for bubble content.                               |
| `TourSkipButton`                    | `@Composable` fun  | Ready-made skip button.                                               |
| `TourStrings`                       | data class         | Built-in overlay texts.                                               |
| `TourSettings`                      | data class         | Spotlight padding, corner radius, missing-target behaviour.           |

If you build the root layout yourself instead of using `TapTargetHost`, provide the controller with
`CompositionLocalProvider(LocalTapTargetController provides controller)` and put
`TapTargetOverlay(controller)` as the last child of a full-size `Box`.

## Behaviour and limitations

- **One composable per id**: an id must be registered by only one composable at a time. If a
  `HorizontalPager` keeps several pages composed that share a screen composable, apply `tapTarget`
  only on the page that should be highlighted.
- **Targets must be composed**: a target that is not in the composition is not found. The step is
  skipped after 2 seconds (or waits forever with `skipMissingTargets = false`).
  - Items of a `LazyColumn` / `LazyRow` that are far off screen are **not** composed. Point the step
    at a visible item, or scroll the list yourself before the step (for example in
    `onNavigateToRoute`).
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
| `Could not find com.github.lerchenflo:kmp-taptarget` | The JitPack repository is missing in `settings.gradle.kts`, or the version tag does not exist. Open `https://jitpack.io/#lerchenflo/kmp-taptarget` to see the builds. |

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
3. Spotlight on the first item of a scrollable list, tap anywhere.
4. Nested navigation: the tour opens a detail screen inside the Home tab and auto-scrolls to a card
   at the bottom of a long page.
5. Custom bubble content with icons, pointing at the Settings tab.
6. Tab switch: the tour moves to the Settings tab by itself.
7. Free-roam step on a nested settings screen: toggle dark mode, then press Continue.
8. Restart button that reads `LocalTapTargetController`.
9. Goodbye info step, back on the Home tab.

Rotate the Android device during the tour: the current step survives.

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
        substitute(module("com.github.lerchenflo:kmp-taptarget")).using(project(":library"))
    }
}
```

Sync Gradle, and the app builds against your local changes. Remove the block before you commit.

## Releasing a new version

1. Try the change in the demo app (and, if needed, in an app with the composite build above).
2. Commit and push to `main`.
3. Create a git tag with the new version and push it, for example:
   ```bash
   git tag 1.0.1
   git push origin 1.0.1
   ```
4. Open `https://jitpack.io/#lerchenflo/kmp-taptarget`, click **Get it** next to the tag, and wait
   for the build. JitPack also builds a tag the first time an app asks for it, but then that first
   Gradle sync waits for the build.
5. Raise the version in the apps' `libs.versions.toml`.

How the JitPack build works: `jitpack.yml` tells JitPack to run
`./gradlew :library:publishToMavenLocal -Pversion=<tag>` with JDK 25 on Linux. The example apps are
not built. Kotlin cross-compiles the iOS libraries on Linux, so iOS works without a Mac. A tag that
was built cannot be rebuilt with different code: fix problems with a new tag. Any commit can be
tried without a tag by using its short hash as version, for example
`com.github.lerchenflo:kmp-taptarget:a1b2c3d`.

## Guide for AI coding assistants

This section is a compact, rule-based summary for tools that add the library to an app.

**Integration checklist**

1. Add `maven("https://jitpack.io")` to `dependencyResolutionManagement.repositories` in
   `settings.gradle.kts`.
2. Add `com.github.lerchenflo:kmp-taptarget:<version>` to the version catalog and to
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
- Do not point a `tapStep` at a lazy list item that may be off screen.
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
    fun infoStep(route: Any? = null, backgroundColor: Color = DefaultTourBackgroundColor, content: @Composable ColumnScope.() -> Unit)
    fun infoStep(title: StringResource? = null, description: StringResource? = null, route: Any? = null, backgroundColor: Color = DefaultTourBackgroundColor)
    fun freeRoamStep(route: Any? = null, position: FreeRoamBarPosition = FreeRoamBarPosition.Bottom, continueButtonText: String? = null, content: @Composable ColumnScope.() -> Unit)
    fun freeRoamStep(title: StringResource? = null, description: StringResource? = null, route: Any? = null, position: FreeRoamBarPosition = FreeRoamBarPosition.Bottom, continueButtonText: StringResource? = null)
}
@Composable fun rememberTapTargetController(tour: TapTargetTour, onNavigateToRoute: suspend (Any) -> Unit = {}, currentRoute: () -> Any? = { null }, isMatchingRoute: (targetRoute: Any, activeRoute: Any) -> Boolean = { t, a -> t::class == a::class }, onFinished: () -> Unit = {}, tourSettings: TourSettings = TourSettings()): TapTargetController
@Composable fun TapTargetHost(controller: TapTargetController, modifier: Modifier = Modifier, strings: TourStrings = TourStrings(), skipButton: (@Composable (onSkip: () -> Unit) -> Unit)? = null, content: @Composable BoxScope.() -> Unit)
fun Modifier.tapTarget(id: String): Modifier
val LocalTapTargetController: ProvidableCompositionLocal<TapTargetController?>
@Composable fun TourTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null)
@Composable fun TourDescription(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null)
@Composable fun TourSkipButton(text: String, onSkip: () -> Unit, modifier: Modifier = Modifier)
data class TourStrings(val tapHighlighted: String, val tapAnywhere: String, val continueButton: String)
data class TourSettings(val iconPadding: Dp = 4.dp, val cornerRadius: Dp = 16.dp, val skipMissingTargets: Boolean = true)
enum class FreeRoamBarPosition { Top, Center, Bottom }
```

## License

Apache License 2.0, see [LICENSE](LICENSE).
