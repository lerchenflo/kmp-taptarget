package io.github.lerchenflo.taptarget.example

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lerchenflo.taptarget.BubbleStyle
import io.github.lerchenflo.taptarget.FreeRoamLeaveBehavior
import io.github.lerchenflo.taptarget.SpotlightShape
import io.github.lerchenflo.taptarget.TapTargetHost
import io.github.lerchenflo.taptarget.TourBackBehavior
import io.github.lerchenflo.taptarget.TourSettings
import io.github.lerchenflo.taptarget.TourSkipButton
import io.github.lerchenflo.taptarget.WrongTapFeedback
import io.github.lerchenflo.taptarget.rememberTapTargetController
import io.github.lerchenflo.taptarget.tapTarget

/**
 * Demo app shared by desktop (`:example`) and Android (`:example-android`).
 *
 * Shows the whole integration in one place: [rememberTapTargetController] wired to the app's
 * navigation, [TapTargetHost] around the content, and `Modifier.tapTarget(id)` on the screens.
 */
@Composable
fun TapTargetExampleApp() {
    val systemDarkTheme = isSystemInDarkTheme()
    var darkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val darkTheme = darkThemeOverride ?: systemDarkTheme

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        val navigator = remember { ExampleNavigator() }
        val homeListState = rememberLazyListState()
        val tour = remember(homeListState) { exampleTour(homeListState) }
        val tourOptions = remember { DemoTourOptions() }

        val tourController = rememberTapTargetController(
            tour = tour,
            onNavigateToRoute = { route -> (route as? ExampleRoute)?.let(navigator::navigate) },
            currentRoute = { navigator.currentRoute },
            // Applied on every composition, so the switches in Settings change the running tour
            tourSettings = tourOptions.toTourSettings(),
            onStepShown = { index, _ -> println("Tour: step ${index + 1} shown") },
            onWrongTap = { index, position -> println("Tour: wrong tap on step ${index + 1} at $position") },
            onSkipped = { index -> println("Tour: skipped on step ${index + 1}") },
            onTargetMissing = { index, id -> println("Tour: target $id of step ${index + 1} not found") },
            onFinished = { println("Tour: finished") },
        )

        // Starts the tour once. A tour that was running before the activity was recreated is
        // restored by rememberTapTargetController and must not be reset by a second start().
        // Real apps keep a "tour seen" flag in their preferences instead.
        var tourStarted by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!tourStarted) {
                tourStarted = true
                tourController.start()
            }
        }

        TapTargetHost(
            controller = tourController,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            skipButton = { onSkip -> TourSkipButton(text = "Skip tour", onSkip = onSkip) },
        ) {
            ExampleScaffold(
                navigator = navigator,
                homeListState = homeListState,
                tourOptions = tourOptions,
                darkTheme = darkTheme,
                onDarkThemeChange = { darkThemeOverride = it },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExampleScaffold(
    navigator: ExampleNavigator,
    homeListState: LazyListState,
    tourOptions: DemoTourOptions,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    val route = navigator.currentRoute
    val itemIds = remember { mutableStateListOf<Int>().apply { addAll(1..30) } }

    // System back pops the nested screen of the current tab
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = navigator.canGoBack,
        onBackCompleted = { navigator.goBack() },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(route.title) },
                navigationIcon = {
                    if (navigator.canGoBack) {
                        IconButton(onClick = { navigator.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                ExampleTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = navigator.currentTab == tab,
                        onClick = { navigator.selectTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        modifier = if (tab == ExampleTab.Settings) {
                            Modifier.tapTarget(ExampleTargets.SETTINGS_TAB)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (route == ExampleRoute.Home) {
                FloatingActionButton(
                    onClick = { itemIds.add(0, (itemIds.maxOrNull() ?: 0) + 1) },
                    modifier = Modifier.tapTarget(ExampleTargets.ADD_BUTTON),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add item")
                }
            }
        },
    ) { contentPadding ->
        when (route) {
            ExampleRoute.Home -> HomeScreen(
                listState = homeListState,
                itemIds = itemIds,
                onItemClick = { itemId -> navigator.navigate(ExampleRoute.ItemDetail(itemId)) },
                contentPadding = contentPadding,
            )
            is ExampleRoute.ItemDetail -> ItemDetailScreen(itemId = route.itemId, contentPadding = contentPadding)
            ExampleRoute.Settings -> SettingsScreen(
                tourOptions = tourOptions,
                onAppearanceClick = { navigator.navigate(ExampleRoute.Appearance) },
                contentPadding = contentPadding,
            )
            ExampleRoute.Appearance -> AppearanceScreen(
                darkTheme = darkTheme,
                onDarkThemeChange = onDarkThemeChange,
                contentPadding = contentPadding,
            )
        }
    }
}

/** Switches on the Settings screen that change the running tour. */
@Stable
class DemoTourOptions {
    var passTapToTarget by mutableStateOf(false)
    var wrongTapFeedback by mutableStateOf(true)
    var circleSpotlight by mutableStateOf(false)
    var showProgress by mutableStateOf(true)
    var backBlocks by mutableStateOf(false)
    var keepOnFreeRoamScreen by mutableStateOf(false)

    fun toTourSettings(): TourSettings = TourSettings(
        passTapToTarget = passTapToTarget,
        wrongTapFeedback = if (wrongTapFeedback) WrongTapFeedback() else WrongTapFeedback.None,
        spotlightShape = if (circleSpotlight) SpotlightShape.Circle else SpotlightShape.RoundedRect,
        bubble = BubbleStyle(showProgress = showProgress),
        backBehavior = if (backBlocks) TourBackBehavior.Block else TourBackBehavior.PreviousStep,
        freeRoamBackBehavior = if (keepOnFreeRoamScreen) TourBackBehavior.Block else TourBackBehavior.PassThrough,
        freeRoamLeaveBehavior = if (keepOnFreeRoamScreen) FreeRoamLeaveBehavior.ReturnToStepRoute else FreeRoamLeaveBehavior.Allow,
    )
}

private val ExampleRoute.title: String
    get() = when (this) {
        ExampleRoute.Home -> "Items"
        is ExampleRoute.ItemDetail -> "Item $itemId"
        ExampleRoute.Settings -> "Settings"
        ExampleRoute.Appearance -> "Appearance"
    }

private val ExampleTab.label: String
    get() = when (this) {
        ExampleTab.Home -> "Home"
        ExampleTab.Settings -> "Settings"
    }

private val ExampleTab.icon: ImageVector
    get() = when (this) {
        ExampleTab.Home -> Icons.Filled.Home
        ExampleTab.Settings -> Icons.Filled.Settings
    }
