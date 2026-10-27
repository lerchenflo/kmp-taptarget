package io.github.lerchenflo.taptarget.example

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lerchenflo.taptarget.LocalTapTargetController
import io.github.lerchenflo.taptarget.tapTarget

/** Home tab root: a scrollable list. The first item is a tour target. */
@Composable
fun HomeScreen(
    itemIds: List<Int>,
    onItemClick: (itemId: Int) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(itemIds, key = { _, itemId -> itemId }) { index, itemId ->
            Card(
                onClick = { onItemClick(itemId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == 0) Modifier.tapTarget(ExampleTargets.FIRST_ITEM) else Modifier),
            ) {
                ListItem(
                    headlineContent = { Text("Item $itemId") },
                    supportingContent = { Text("Opens a detail screen in this tab") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                )
            }
        }
    }
}

/** Nested screen of the Home tab: a long page. The notes card at the bottom is a tour target. */
@Composable
fun ItemDetailScreen(itemId: Int, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Item $itemId", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A long page, so the tour has to scroll to reach the notes card at the bottom.",
            style = MaterialTheme.typography.bodyLarge,
        )

        repeat(8) { index ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Section ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Placeholder content that makes the page longer than the screen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().tapTarget(ExampleTargets.DETAIL_NOTES)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Notes", style = MaterialTheme.typography.titleMedium)
                Text("The tour scrolled here by itself.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Settings tab root. */
@Composable
fun SettingsScreen(onAppearanceClick: () -> Unit, contentPadding: PaddingValues) {
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }
    // Any screen below TapTargetHost can read the controller, e.g. to restart the tour
    val tourController = LocalTapTargetController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        ListItem(
            headlineContent = { Text("Appearance") },
            supportingContent = { Text("Opens a nested screen") },
            leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier
                .tapTarget(ExampleTargets.APPEARANCE_ENTRY)
                .clickable(onClick = onAppearanceClick),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Notifications") },
            leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
            trailingContent = {
                Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
            },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("About") },
            supportingContent = { Text("kmp-taptarget demo app") },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
        )

        Button(
            onClick = { tourController?.start() },
            modifier = Modifier
                .padding(16.dp)
                .tapTarget(ExampleTargets.RESTART_BUTTON),
        ) {
            Text("Restart tour")
        }
    }
}

/** Nested screen of the Settings tab, used by the free-roam step. */
@Composable
fun AppearanceScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    var largeText by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        ListItem(
            headlineContent = { Text("Dark mode") },
            leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null) },
            trailingContent = { Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange) },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Large text") },
            supportingContent = { Text("Does nothing, it is only here to be tapped") },
            leadingContent = { Icon(Icons.Filled.TextFields, contentDescription = null) },
            trailingContent = { Switch(checked = largeText, onCheckedChange = { largeText = it }) },
        )
    }
}
