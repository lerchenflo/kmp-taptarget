package io.github.lerchenflo.taptarget.example

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Every screen of the demo. Tour steps use these as their `route`. */
sealed interface ExampleRoute {
    data object Home : ExampleRoute
    data class ItemDetail(val itemId: Int) : ExampleRoute
    data object Settings : ExampleRoute
    data object Appearance : ExampleRoute
}

/** Bottom bar tabs. Each tab has its own back stack, starting at [root]. */
enum class ExampleTab(val root: ExampleRoute) {
    Home(ExampleRoute.Home),
    Settings(ExampleRoute.Settings),
}

val ExampleRoute.tab: ExampleTab
    get() = when (this) {
        ExampleRoute.Home, is ExampleRoute.ItemDetail -> ExampleTab.Home
        ExampleRoute.Settings, ExampleRoute.Appearance -> ExampleTab.Settings
    }

/**
 * Minimal nested navigation: one back stack per tab, like most real apps.
 * The library does not depend on it - any navigation library works the same way.
 */
@Stable
class ExampleNavigator {
    var currentTab by mutableStateOf(ExampleTab.Home)
        private set

    private val backStacks = ExampleTab.entries.associateWith { tab -> mutableStateListOf(tab.root) }

    /** Visible screen: the top of the current tab's back stack. */
    val currentRoute: ExampleRoute
        get() = backStacks.getValue(currentTab).last()

    val canGoBack: Boolean
        get() = backStacks.getValue(currentTab).size > 1

    fun selectTab(tab: ExampleTab) {
        currentTab = tab
    }

    /** Opens [route] in its tab. A tab root clears that tab's back stack instead of stacking a copy. */
    fun navigate(route: ExampleRoute) {
        val tab = route.tab
        val stack = backStacks.getValue(tab)
        currentTab = tab
        if (route == tab.root) {
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else if (stack.last() != route) {
            stack.add(route)
        }
    }

    fun goBack() {
        val stack = backStacks.getValue(currentTab)
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
