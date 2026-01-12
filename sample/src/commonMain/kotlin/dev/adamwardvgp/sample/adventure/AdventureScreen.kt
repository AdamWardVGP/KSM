package dev.adamwardvgp.sample.adventure

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.adamwardvgp.sample.adventure.AdventureEvent.*

@Composable
fun AdventureScreen(adventureViewModel: AdventureViewModel) {
    val state by adventureViewModel.adventureState.collectAsState()

    when (state) {
        AdventureState.Start ->
            AdventureDialog(
                title = "Welcome",
                text = "Your adventure begins here...",
                buttons = listOf("I'm Ready!" to {
                    adventureViewModel.dispatchEvent(Begin)
                })
            )

        AdventureState.DarkForest ->
            AdventureDialog(
                title = "Dark Forest",
                text = "You stand in a dark forest. Two paths lie ahead.",
                buttons = listOf(
                    "Go Left" to { adventureViewModel.dispatchEvent(GoLeft) },
                    "Go Right" to { adventureViewModel.dispatchEvent(GoRight) }
                )
            )

        AdventureState.OldBridge ->
            AdventureDialog(
                title = "Old Bridge",
                text = "A rickety bridge crosses a deep chasm.",
                buttons = listOf(
                    "Cross the bridge" to { adventureViewModel.dispatchEvent(CrossBridge) },
                    "Turn back" to { adventureViewModel.dispatchEvent(RunAway) }
                )
            )

        is AdventureState.FightMonster ->
            AdventureDialog(
                title = "A Monster!",
                text = "A ${(state as AdventureState.FightMonster).monster} attacks!",
                buttons = listOf(
                    "Fight" to {
                        adventureViewModel.dispatchEvent(Fight())
                   },
                    "Run Away" to { adventureViewModel.dispatchEvent(RunAway) }
                )
            )

        AdventureState.Treasure ->
            AdventureDialog(
                title = "Victory!",
                text = "You found the treasure 🏆",
                buttons = listOf(
                    "Play Again" to {
                        adventureViewModel.dispatchEvent(Restart)
                    }
                )
            )

        is AdventureState.GameOver ->
            AdventureDialog(
                title = "☠ Game Over",
                text = (state as AdventureState.GameOver).reason,
                buttons = listOf(
                    "Play Again" to {
                        adventureViewModel.dispatchEvent(Restart)
                    }
                )
            )

        AdventureState.CaveEntrance -> {
            AdventureDialog(
                title = "You find a spooky cave",
                text = "You can see the exit of the cave but it still frightens you.",
                buttons = listOf(
                    "Enter the cave" to {
                        // Generate a random monster, probably shouldn't do this from the UI but you get
                        // the idea - you can pass event inputs into your graph.
                        adventureViewModel.dispatchEvent(EnterCave(randomMonster()))
                    },
                    "Turn back" to {
                        adventureViewModel.dispatchEvent(RunAway)
                    }
                )
            )
        }
    }
}

@Composable
fun AdventureDialog(
    title: String,
    text: String,
    buttons: List<Pair<String, () -> Unit>>
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Row {
                Spacer(modifier = Modifier.weight(1.0f))
                buttons.forEach { (label, action) ->
                    Button(onClick = action) {
                        Text(label)
                    }
                    Spacer(modifier = Modifier.weight(1.0f))
                }
            }
        }
    )
}
