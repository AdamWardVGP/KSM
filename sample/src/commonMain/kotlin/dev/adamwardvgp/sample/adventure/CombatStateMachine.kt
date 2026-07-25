package dev.adamwardvgp.sample.adventure

import coffee.adammakes.ksm.StateMachine
import coffee.adammakes.ksm.stateMachine
import kotlinx.coroutines.CoroutineScope

/**
 * A self-contained turn-based combat mini-game, reused as a composite child inside
 * [AdventureState.FightMonster] — see [getAdventureStateMachine].
 *
 * Knows nothing about [AdventureState]/[AdventureEvent]; a fresh instance is created every time
 * combat is entered, so the same definition is safe to reuse across encounters.
 */
sealed interface CombatState {
  data class Fighting(val playerHp: Int, val monsterHp: Int) : CombatState

  object Won : CombatState

  object Lost : CombatState
}

sealed interface CombatEvent {
  object Attack : CombatEvent

  object Defend : CombatEvent
}

private const val STARTING_HP = 20
private const val ATTACK_DAMAGE = 8
private const val ATTACK_COUNTER_DAMAGE = 4
private const val DEFEND_COUNTER_DAMAGE = 2

fun getCombatStateMachine(coroutineScope: CoroutineScope): StateMachine<CombatState, CombatEvent> =
  stateMachine<CombatState, CombatEvent> {
    initialState = CombatState.Fighting(playerHp = STARTING_HP, monsterHp = STARTING_HP)
    dispatchedOn = coroutineScope

    state<CombatState.Fighting> {
      on<CombatEvent.Attack>() transitionWith
        { state, _ ->
          state.resolveRound(ATTACK_DAMAGE, ATTACK_COUNTER_DAMAGE)
        }
      on<CombatEvent.Defend>() transitionWith
        { state, _ ->
          state.resolveRound(0, DEFEND_COUNTER_DAMAGE)
        }
    }

    state<CombatState.Won> {}
    state<CombatState.Lost> {}
  }

private fun CombatState.Fighting.resolveRound(damageDealt: Int, damageTaken: Int): CombatState {
  val monsterHp = (monsterHp - damageDealt).coerceAtLeast(0)
  if (monsterHp <= 0) return CombatState.Won

  val playerHp = (playerHp - damageTaken).coerceAtLeast(0)
  if (playerHp <= 0) return CombatState.Lost

  return copy(playerHp = playerHp, monsterHp = monsterHp)
}
