package dev.adamwardvgp.sample.adventure

import kotlinx.coroutines.CoroutineScope
import org.example.StateMachine
import org.example.stateMachine

fun getAdventureStateMachine(coroutineScope: CoroutineScope): StateMachine<AdventureState, AdventureEvent> {
    return stateMachine<AdventureState, AdventureEvent> {

        initialState = AdventureState.Start
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
            on<AdventureEvent.RunAway>().transitionWith<AdventureState.GameOver> { _, _ ->
                AdventureState.GameOver("You lose your way and starve in the woods.")
            }
        }

        state<AdventureState.CaveEntrance> {
            on<AdventureEvent.EnterCave>().transitionWith<AdventureState.FightMonster> { _, event ->
                AdventureState.FightMonster(event.monsterName)
            }

            on<AdventureEvent.RunAway>() transitionTo AdventureState.DarkForest
        }

        state<AdventureState.FightMonster> {
            on<AdventureEvent.Fight>() transitionTo AdventureState.Treasure
            on<AdventureEvent.RunAway>().transitionWith<AdventureState.GameOver> { _, _ ->
                AdventureState.GameOver("You try to get away but trip and are eaten by the monster.")
            }
        }

        state<AdventureState.GameOver> {
            on<AdventureEvent.Restart>() transitionTo AdventureState.Start
        }

        state<AdventureState.Treasure> {
            on<AdventureEvent.Restart>() transitionTo AdventureState.Start
        }
    }
}


sealed interface AdventureEvent {
    object Begin : AdventureEvent

    object GoLeft : AdventureEvent
    object GoRight : AdventureEvent

    object CrossBridge : AdventureEvent
    data class EnterCave(val monsterName: String)  : AdventureEvent

    class Fight: AdventureEvent
    object RunAway : AdventureEvent

    object Restart : AdventureEvent
}

sealed interface AdventureState {
    object Start : AdventureState
    object DarkForest : AdventureState
    object OldBridge : AdventureState
    object CaveEntrance : AdventureState

    data class FightMonster(val monster: String) : AdventureState

    object Treasure : AdventureState
    data class GameOver(val reason: String) : AdventureState
}

