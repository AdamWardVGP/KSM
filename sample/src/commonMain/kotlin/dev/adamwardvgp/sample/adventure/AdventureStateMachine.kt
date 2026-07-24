package dev.adamwardvgp.sample.adventure

import coffee.adammakes.ksm.StateMachine
import coffee.adammakes.ksm.stateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable

fun getAdventureStateMachine(
  coroutineScope: CoroutineScope,
  initialState: AdventureState = AdventureState.Start,
): StateMachine<AdventureState, AdventureEvent> {
  return stateMachine<AdventureState, AdventureEvent> {
    this.initialState = initialState
    dispatchedOn = coroutineScope

    state<AdventureState.Start> {
      on<AdventureEvent.Begin>() transitionTo AdventureState.DarkForest
    }

    state<AdventureState.DarkForest> {
      on<AdventureEvent.GoLeft>() transitionTo AdventureState.OldBridge
      on<AdventureEvent.GoRight>() transitionTo AdventureState.CaveEntrance
    }

    state<AdventureState.OldBridge> {
      on<AdventureEvent.CrossBridge>() transitionTo AdventureState.Treasure
      on<AdventureEvent.RunAway>() transitionWith
        { _, _ ->
          AdventureState.GameOver("You lose your way and starve in the woods.")
        }
    }

    state<AdventureState.CaveEntrance> {
      on<AdventureEvent.EnterCave>() transitionWith
        { _, event ->
          AdventureState.FightMonster(event.monsterName)
        }

      on<AdventureEvent.RunAway>() transitionTo AdventureState.DarkForest
    }

    state<AdventureState.FightMonster> {
      on<AdventureEvent.Fight>() transitionTo AdventureState.Treasure
      on<AdventureEvent.RunAway>() transitionWith
        { _, _ ->
          AdventureState.GameOver("You try to get away but trip and are eaten by the monster.")
        }
    }

    state<AdventureState.Finished> {
      on<AdventureEvent.Restart>() transitionTo AdventureState.Start

      state<AdventureState.GameOver> {}
      state<AdventureState.Treasure> {}
    }
  }
}

sealed interface AdventureEvent {
  object Begin : AdventureEvent

  object GoLeft : AdventureEvent

  object GoRight : AdventureEvent

  object CrossBridge : AdventureEvent

  data class EnterCave(val monsterName: String) : AdventureEvent

  class Fight : AdventureEvent

  object RunAway : AdventureEvent

  object Restart : AdventureEvent
}

@Serializable
sealed interface AdventureState {
  @Serializable object Start : AdventureState

  @Serializable object DarkForest : AdventureState

  @Serializable object OldBridge : AdventureState

  @Serializable object CaveEntrance : AdventureState

  @Serializable data class FightMonster(val monster: String) : AdventureState

  @Serializable sealed interface Finished : AdventureState

  @Serializable object Treasure : Finished

  @Serializable data class GameOver(val reason: String) : Finished
}
