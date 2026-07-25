package dev.adamwardvgp.sample.adventure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coffee.adammakes.ksm.effects.withEffects
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

interface AdventureViewModel {
  val adventureState: StateFlow<AdventureState>
  val combatState: StateFlow<CombatState?>
  val emojiRain: SharedFlow<String>

  fun dispatchEvent(event: AdventureEvent)

  fun dispatchCombatEvent(event: CombatEvent)
}

class AdventureViewModelImpl(private val savedStateHandle: SavedStateHandle) :
  ViewModel(), AdventureViewModel {

  private val restoredState: AdventureState? =
    savedStateHandle.get<String>(KEY_STATE)?.let {
      runCatching { Json.decodeFromString<AdventureState>(it) }.getOrNull()
    }

  private val _emojiRain = MutableSharedFlow<String>(extraBufferCapacity = 64)
  override val emojiRain: SharedFlow<String> = _emojiRain

  private val machines =
    getAdventureStateMachine(
      viewModelScope,
      restoredState ?: AdventureState.Start,
      emojiSink = _emojiRain,
    )
  private val machine = machines.adventure
  private val combat = machines.combat

  private val effectedMachine =
    machine.withEffects(viewModelScope) {
      onEnter<AdventureState.Treasure>() effect ::rainCoins
      onEnter<AdventureState.GameOver>() effect ::rainSkulls
    }

  override val adventureState = effectedMachine.currentState
  override val combatState = combat.activeChildState

  init {
    viewModelScope.launch {
      machine.currentState.collect { state ->
        savedStateHandle[KEY_STATE] = Json.encodeToString(state)
      }
    }
  }

  override fun dispatchEvent(event: AdventureEvent) = effectedMachine.dispatchEvent(event)

  override fun dispatchCombatEvent(event: CombatEvent) = combat.dispatch(event)

  private suspend fun rainCoins(
    @Suppress("UNUSED_PARAMETER") state: AdventureState.Treasure
  ): AdventureEvent {
    repeat(30) {
      _emojiRain.emit("🪙")
      delay(120)
    }
    awaitCancellation()
  }

  private suspend fun rainSkulls(
    @Suppress("UNUSED_PARAMETER") state: AdventureState.GameOver
  ): AdventureEvent {
    repeat(20) {
      _emojiRain.emit("💀")
      delay(150)
    }
    awaitCancellation()
  }

  companion object {
    private const val KEY_STATE = "adventure_state"
  }
}

val monsters =
  listOf(
    "\uD83E\uDDCC Cave Troll",
    "\uD83D\uDC7A Goblin King",
    "\uD83D\uDD77\uFE0F Ancient Spider",
    "♘ Evil Stallion",
    "\uD83D\uDC80 Skeleton Knight",
  )

fun randomMonster(): String = monsters.random()
