package dev.adamwardvgp.sample.adventure

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
  Window(onCloseRequest = ::exitApplication, title = "KSMSample") { AdventureAppRoot() }
}
