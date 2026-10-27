package io.github.lerchenflo.taptarget.example

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lerchenflo.taptarget.FreeRoamBarPosition
import io.github.lerchenflo.taptarget.TapTargetTour
import io.github.lerchenflo.taptarget.TourDescription
import io.github.lerchenflo.taptarget.TourTitle
import io.github.lerchenflo.taptarget.tapTargetTour

/** Ids shared by the tour steps and the `Modifier.tapTarget(id)` calls on the screens. */
object ExampleTargets {
    const val ADD_BUTTON = "add_button"
    const val FIRST_ITEM = "first_item"
    const val DETAIL_NOTES = "detail_notes"
    const val SETTINGS_TAB = "settings_tab"
    const val APPEARANCE_ENTRY = "appearance_entry"
    const val RESTART_BUTTON = "restart_button"
}

/** The item whose detail screen the tour opens. */
const val TOUR_ITEM_ID = 3

/** One step per library feature, in the order a user meets them. */
val exampleTour: TapTargetTour = tapTargetTour {
    infoStep(route = ExampleRoute.Home) {
        TourTitle("Welcome")
        TourDescription("This demo shows every feature of kmp-taptarget. Follow the steps or skip the tour at any time.")
    }

    tapStep(id = ExampleTargets.ADD_BUTTON, route = ExampleRoute.Home) {
        TourTitle("Spotlight step")
        TourDescription("Only a tap on the highlighted button continues. The tap does not reach the button itself.")
    }

    tapStep(id = ExampleTargets.FIRST_ITEM, route = ExampleRoute.Home, requireExactTap = false) {
        TourTitle("Tap anywhere")
        TourDescription("This step only points at something. A tap anywhere continues.")
    }

    tapStep(
        id = ExampleTargets.DETAIL_NOTES,
        route = ExampleRoute.ItemDetail(TOUR_ITEM_ID),
        requireExactTap = false,
    ) {
        TourTitle("Nested navigation and auto-scroll")
        TourDescription(
            "The tour opened a detail screen inside the Home tab and scrolled this card, " +
                "far down the page, to the middle of the screen."
        )
    }

    tapStep(id = ExampleTargets.SETTINGS_TAB) {
        TourTitle("Custom bubble content")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            TourDescription("Bubbles hold any composable. Each tab keeps its own back stack.")
        }
    }

    tapStep(id = ExampleTargets.APPEARANCE_ENTRY, route = ExampleRoute.Settings) {
        TourTitle("Other tab")
        TourDescription("The tour switched to the Settings tab by itself.")
    }

    freeRoamStep(route = ExampleRoute.Appearance, position = FreeRoamBarPosition.Bottom) {
        TourTitle("Free roam")
        TourDescription("The app stays usable. Try the dark mode switch, then press Continue.")
    }

    tapStep(id = ExampleTargets.RESTART_BUTTON, route = ExampleRoute.Settings, requireExactTap = false) {
        TourTitle("Restart the tour")
        TourDescription("This button calls start() on the controller from LocalTapTargetController.")
    }

    infoStep(route = ExampleRoute.Home) {
        TourTitle("Done")
        TourDescription("Rotate the device during the tour: the current step survives. See the README to build your own tour.")
    }
}
