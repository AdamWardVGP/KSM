package dev.adamwardvgp.sample.adventure

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.adamwardvgp.sample.adventure.AdventureEvent.Begin
import dev.adamwardvgp.sample.adventure.AdventureEvent.CrossBridge
import dev.adamwardvgp.sample.adventure.AdventureEvent.EnterCave
import dev.adamwardvgp.sample.adventure.AdventureEvent.GoLeft
import dev.adamwardvgp.sample.adventure.AdventureEvent.GoRight
import dev.adamwardvgp.sample.adventure.AdventureEvent.Restart
import dev.adamwardvgp.sample.adventure.AdventureEvent.RunAway
import kotlin.random.Random
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@Composable
fun AdventureScreen(adventureViewModel: AdventureViewModel) {
  val state by adventureViewModel.adventureState.collectAsState()

  Box(Modifier.fillMaxSize()) {
    when (state) {
      AdventureState.Start ->
        AdventureDialog(
          title = "Welcome",
          text = "Your adventure begins here...",
          buttons = listOf("I'm Ready!" to { adventureViewModel.dispatchEvent(Begin) }),
        )

      AdventureState.DarkForest ->
        AdventureDialog(
          title = "Dark Forest",
          text = "You stand in a dark forest. Two paths lie ahead.",
          buttons =
            listOf(
              "Go Left" to { adventureViewModel.dispatchEvent(GoLeft) },
              "Go Right" to { adventureViewModel.dispatchEvent(GoRight) },
            ),
        )

      AdventureState.OldBridge ->
        AdventureDialog(
          title = "Old Bridge",
          text = "A rickety bridge crosses a deep chasm.",
          buttons =
            listOf(
              "Cross the bridge" to { adventureViewModel.dispatchEvent(CrossBridge) },
              "Turn back" to { adventureViewModel.dispatchEvent(RunAway) },
            ),
        )

      is AdventureState.FightMonster -> {
        val monster = (state as AdventureState.FightMonster).monster
        val combat by adventureViewModel.combatState.collectAsState()
        when (val fight = combat) {
          is CombatState.PlayerTurn -> {
            val context = fight.context
            AdventureDialog(
              title = "A $monster attacks!",
              text =
                "You: ${context.playerHp} HP — ${context.enemyType.displayName}: ${context.monsterHp} HP",
              buttons =
                listOfNotNull(
                  "Attack" to { adventureViewModel.dispatchCombatEvent(CombatEvent.Attack) },
                  if (!context.playerHasHealed) {
                    "Defend (heal)" to
                      {
                        adventureViewModel.dispatchCombatEvent(CombatEvent.Defend)
                      }
                  } else {
                    null
                  },
                  "Special Move" to
                    {
                      adventureViewModel.dispatchCombatEvent(CombatEvent.SpecialMove)
                    },
                  "Run Away" to { adventureViewModel.dispatchEvent(RunAway) },
                ),
            )
          }
          is CombatState.PlayerReact -> {
            val context = fight.context
            val telegraphText =
              if (fight.telegraphedHit == HitType.BIG) "The $monster winds up a big hit!"
              else "The $monster strikes at you!"
            AdventureDialog(
              title = telegraphText,
              text =
                "You: ${context.playerHp} HP — ${context.enemyType.displayName}: ${context.monsterHp} HP",
              buttons =
                listOfNotNull(
                  if (!context.specialLockout) {
                    "Dodge" to { adventureViewModel.dispatchCombatEvent(CombatEvent.Dodge) }
                  } else {
                    null
                  },
                  if (!context.specialLockout) {
                    "Block" to { adventureViewModel.dispatchCombatEvent(CombatEvent.Block) }
                  } else {
                    null
                  },
                  if (!context.playerHasInvisibility) {
                    "Turn Invisible" to
                      {
                        adventureViewModel.dispatchCombatEvent(CombatEvent.Invisible)
                      }
                  } else {
                    null
                  },
                  "Stand and Take It" to
                    {
                      adventureViewModel.dispatchCombatEvent(CombatEvent.Stand)
                    },
                ),
            )
          }
          is CombatState.Won ->
            AdventureDialog(
              title = "Victory!",
              text = fight.reason.narrate(won = true),
              buttons = emptyList(),
            )
          is CombatState.Lost ->
            AdventureDialog(
              title = "Defeated...",
              text = fight.reason.narrate(won = false),
              buttons = emptyList(),
            )
          // EnemyTelegraph/EnemyResolve are transient — their onEnter effects resolve and
          // dispatch onward immediately, so there's nothing meaningful to render for them.
          is CombatState.EnemyTelegraph,
          is CombatState.EnemyResolve,
          null ->
            AdventureDialog(
              title = "A Monster!",
              text = "A $monster attacks!",
              buttons = emptyList(),
            )
        }
      }

      AdventureState.Treasure ->
        AdventureDialog(
          title = "Victory!",
          text = "You found the treasure 🏆",
          buttons = listOf("Play Again" to { adventureViewModel.dispatchEvent(Restart) }),
        )

      is AdventureState.GameOver ->
        AdventureDialog(
          title = "☠ Game Over",
          text = (state as AdventureState.GameOver).reason,
          buttons = listOf("Play Again" to { adventureViewModel.dispatchEvent(Restart) }),
        )

      AdventureState.CaveEntrance ->
        AdventureDialog(
          title = "You find a spooky cave",
          text = "You can see the exit of the cave but it still frightens you.",
          buttons =
            listOf(
              "Enter the cave" to { adventureViewModel.dispatchEvent(EnterCave(randomMonster())) },
              "Turn back" to { adventureViewModel.dispatchEvent(RunAway) },
            ),
        )
    }

    EmojiRain(adventureViewModel.emojiRain)
  }
}

private fun Outcome.narrate(won: Boolean): String =
  when (this) {
    Outcome.FINAL_BLOW ->
      if (won) "The monster got one desperate hit in before falling."
      else "You landed one last hit, but it wasn't enough."
    Outcome.MUTUAL_KILL -> "You and the monster take each other down together."
  }

private data class FallingEmoji(
  val emoji: String,
  val x: Float,
  val progress: Animatable<Float, AnimationVector1D>,
)

@Composable
fun EmojiRain(flow: SharedFlow<String>) {
  val falling = remember { mutableStateListOf<FallingEmoji>() }

  LaunchedEffect(flow) {
    val scope = this
    flow.collect { emoji ->
      val entry = FallingEmoji(emoji, Random.nextFloat() * 0.88f, Animatable(0f))
      falling += entry
      scope.launch {
        entry.progress.animateTo(1f, animationSpec = tween(2000, easing = LinearEasing))
        falling -= entry
      }
    }
  }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val w = maxWidth
    val h = maxHeight
    falling.forEach { entry ->
      Text(
        entry.emoji,
        fontSize = 28.sp,
        modifier = Modifier.offset(x = w * entry.x, y = h * entry.progress.value),
      )
    }
  }
}

@Composable
fun AdventureDialog(title: String, text: String, buttons: List<Pair<String, () -> Unit>>) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      modifier = Modifier.padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(title, style = MaterialTheme.typography.headlineMedium)
      Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        buttons.forEach { (label, action) -> Button(onClick = action) { Text(label) } }
      }
    }
  }
}
