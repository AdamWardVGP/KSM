package dev.adamwardvgp.sample.adventure

import coffee.adammakes.ksm.CompositeHandle
import coffee.adammakes.ksm.StateMachine
import coffee.adammakes.ksm.stateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable

/**
 * The adventure's own [StateMachine], plus a [combat] handle for the composite child embedded in
 * [AdventureState.FightMonster] — see the "Composite states" section of the README for why these
 * are two separate typed entry points rather than one.
 */
class AdventureMachines(
  val adventure: StateMachine<AdventureState, AdventureEvent>,
  val combat: CompositeHandle<AdventureEvent, CombatEvent, CombatState>,
)

fun getAdventureStateMachine(
  coroutineScope: CoroutineScope,
  initialState: AdventureState = AdventureState.Start,
): AdventureMachines {
  lateinit var combat: CompositeHandle<AdventureEvent, CombatEvent, CombatState>

  val adventure =
    stateMachine<AdventureState, AdventureEvent> {
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
        // Parent-level interrupt: fires regardless of how the fight is going, bypassing combat
        // entirely — the composite's own transitions are always tried before its child.
        on<AdventureEvent.RunAway>() transitionWith
          { _, _ ->
            AdventureState.GameOver("You try to get away but trip and are eaten by the monster.")
          }
        on<AdventureEvent.MonsterDefeated>() transitionTo AdventureState.Treasure
        on<AdventureEvent.PlayerDefeated>() transitionWith
          { _, _ ->
            AdventureState.GameOver("The monster overpowers you in combat.")
          }

        // Fresh CombatStateMachine every time a fight is entered — the same definition is reused
        // for every monster encounter without carrying HP over between them.
        combat =
          child(factory = { getCombatStateMachine(coroutineScope) }) {
            exit<CombatState.Won> { AdventureEvent.MonsterDefeated }
            exit<CombatState.Lost> { AdventureEvent.PlayerDefeated }
          }
      }

      state<AdventureState.Finished> {
        on<AdventureEvent.Restart>() transitionTo AdventureState.Start

        state<AdventureState.GameOver> {}
        state<AdventureState.Treasure> {}
      }
    }

  return AdventureMachines(adventure, combat)
}

sealed interface AdventureEvent {
  object Begin : AdventureEvent

  object GoLeft : AdventureEvent

  object GoRight : AdventureEvent

  object CrossBridge : AdventureEvent

  data class EnterCave(val monsterName: String) : AdventureEvent

  object MonsterDefeated : AdventureEvent

  object PlayerDefeated : AdventureEvent

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
