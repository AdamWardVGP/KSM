package dev.adamwardvgp.sample.adventure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

interface AdventureViewModel {
  val adventureState: StateFlow<AdventureState>

  fun dispatchEvent(event: AdventureEvent)
}

class AdventureViewModelImpl(private val savedStateHandle: SavedStateHandle) :
  ViewModel(), AdventureViewModel {

  private val restoredState: AdventureState? =
    savedStateHandle.get<String>(KEY_STATE)?.let {
      runCatching { Json.decodeFromString<AdventureState>(it) }.getOrNull()
    }

  private val stateMachine =
    getAdventureStateMachine(viewModelScope, restoredState ?: AdventureState.Start)

  override val adventureState = stateMachine.currentState

  init {
    viewModelScope.launch {
      stateMachine.currentState.collect { state ->
        savedStateHandle[KEY_STATE] = Json.encodeToString(state)
      }
    }
  }

  override fun dispatchEvent(event: AdventureEvent) {
    stateMachine.dispatchEvent(event)
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
