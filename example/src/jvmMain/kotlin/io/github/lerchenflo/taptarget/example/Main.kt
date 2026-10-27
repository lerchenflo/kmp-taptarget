package io.github.lerchenflo.taptarget.example

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "kmp-taptarget example",
        //Phone-like window, so the demo looks the same as on a device
        state = rememberWindowState(width = 420.dp, height = 820.dp),
    ) {
        TapTargetExampleApp()
    }
}
