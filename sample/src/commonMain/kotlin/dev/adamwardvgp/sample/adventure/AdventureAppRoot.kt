package dev.adamwardvgp.sample.adventure

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun AdventureAppRoot() {
  MaterialTheme {
    AdventureScreen(
      adventureViewModel =
        viewModel<AdventureViewModelImpl>(
          factory =
            viewModelFactory {
              initializer { AdventureViewModelImpl(createAdventureSavedStateHandle()) }
            }
        )
    )
  }
}
