package dev.adamwardvgp.sample.adventure


import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
@Composable
@Preview
fun AdventureAppRoot() {
    MaterialTheme {
        AdventureScreen(adventureViewModel = viewModel<AdventureViewModelImpl>())
    }
}