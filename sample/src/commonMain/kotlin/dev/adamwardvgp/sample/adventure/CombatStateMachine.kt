package dev.adamwardvgp.sample.adventure

import coffee.adammakes.ksm.StateMachine
import coffee.adammakes.ksm.effects.withEffects
import coffee.adammakes.ksm.stateMachine
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A self-contained turn-based combat mini-game, reused as a composite child inside
 * [AdventureState.FightMonster] — see [getAdventureStateMachine].
 *
 * A round is a chain of single-outcome states: [CombatState.PlayerTurn] (the player attacks,
 * defends, or unleashes a special move) leads to [CombatState.EnemyTelegraph] (an `onEnter` effect
 * rolls and announces the enemy's next move), which leads to [CombatState.PlayerReact] (the player
 * reacts to the telegraphed hit), which leads to [CombatState.EnemyResolve] (an `onEnter` effect
 * applies the round's damage and loops back to a fresh [CombatState.PlayerTurn] or ends the fight).
 * Death checks and the automatic "final blow" counter-strike live in the same two effects, never in
 * a reducer branching to multiple sibling types.
 *
 * Knows nothing about [AdventureState]/[AdventureEvent]; a fresh instance is created every time
 * combat is entered, so the same definition is safe to reuse across encounters.
 */
sealed interface CombatState {
  data class PlayerTurn(val context: CombatContext) : CombatState

  data class EnemyTelegraph(val context: CombatContext, val lastPlayerAction: PlayerAction) :
    CombatState

  data class PlayerReact(val context: CombatContext, val telegraphedHit: HitType) : CombatState

  data class EnemyResolve(val context: CombatContext, val hitLanded: Boolean) : CombatState

  data class Won(val reason: Outcome) : CombatState

  data class Lost(val reason: Outcome) : CombatState
}

/** Fields carried through every round of a single fight. */
data class CombatContext(
  val playerHp: Int,
  val monsterHp: Int,
  val enemyType: EnemyType,
  val playerHasHealed: Boolean = false,
  val playerHasInvisibility: Boolean = false,
  val specialLockout: Boolean = false,
)

enum class HitType {
  NORMAL,
  BIG,
}

enum class PlayerAction {
  ATTACK,
  DEFEND,
  SPECIAL_MOVE,
}

enum class Outcome {
  FINAL_BLOW,
  MUTUAL_KILL,
}

/** Enemy variance: HP pool, damage rolls, and how often a big hit is telegraphed. */
data class EnemyType(
  val displayName: String,
  val maxHp: Int,
  val normalDamageRange: IntRange,
  val bigHitDamageRange: IntRange,
  val bigHitChancePercent: Int,
)

val ENEMY_TYPES =
  listOf(
    EnemyType(
      displayName = "Goblin",
      maxHp = 18,
      normalDamageRange = 6..10,
      bigHitDamageRange = 14..18,
      bigHitChancePercent = 20,
    ),
    EnemyType(
      displayName = "Ogre",
      maxHp = 28,
      normalDamageRange = 8..12,
      bigHitDamageRange = 18..24,
      bigHitChancePercent = 45,
    ),
  )

sealed interface CombatEvent {
  object Attack : CombatEvent

  object Defend : CombatEvent

  object SpecialMove : CombatEvent

  data class TelegraphResolved(val hitType: HitType) : CombatEvent

  object Dodge : CombatEvent

  object Block : CombatEvent

  object Invisible : CombatEvent

  object Stand : CombatEvent

  object RoundContinues : CombatEvent

  data class FightWon(val reason: Outcome) : CombatEvent

  data class FightLost(val reason: Outcome) : CombatEvent
}

private const val PLAYER_STARTING_HP = 20
private val ATTACK_DAMAGE_RANGE = 8..12
private val SPECIAL_DAMAGE_RANGE = 15..25
private val HEAL_RANGE = 5..15
private val FINAL_BLOW_RANGE = 5..15
private const val DODGE_NORMAL_EVADE_CHANCE_PERCENT = 40
private const val BLOCK_NORMAL_DAMAGE_PERCENT = 50
private const val BLOCK_BIG_DAMAGE_PERCENT = 75

fun getCombatStateMachine(
  coroutineScope: CoroutineScope,
  random: Random = Random.Default,
  enemyType: EnemyType = ENEMY_TYPES.random(random),
  emojiSink: MutableSharedFlow<String>? = null,
): StateMachine<CombatState, CombatEvent> {
  val machine =
    stateMachine<CombatState, CombatEvent> {
      initialState =
        CombatState.PlayerTurn(
          CombatContext(
            playerHp = PLAYER_STARTING_HP,
            monsterHp = enemyType.maxHp,
            enemyType = enemyType,
          )
        )
      dispatchedOn = coroutineScope

      state<CombatState.PlayerTurn> {
        on<CombatEvent.Attack>() transitionWith
          { state, _ ->
            val damage = ATTACK_DAMAGE_RANGE.roll(random)
            CombatState.EnemyTelegraph(
              context = state.context.dealDamageToMonster(damage),
              lastPlayerAction = PlayerAction.ATTACK,
            )
          }
        on<CombatEvent.Defend>() transitionWith
          { state, _ ->
            CombatState.EnemyTelegraph(
              context = state.context.heal(random),
              lastPlayerAction = PlayerAction.DEFEND,
            )
          }
        on<CombatEvent.SpecialMove>() transitionWith
          { state, _ ->
            val damage = SPECIAL_DAMAGE_RANGE.roll(random)
            CombatState.EnemyTelegraph(
              context = state.context.dealDamageToMonster(damage).copy(specialLockout = true),
              lastPlayerAction = PlayerAction.SPECIAL_MOVE,
            )
          }
      }

      state<CombatState.EnemyTelegraph> {
        on<CombatEvent.TelegraphResolved>() transitionWith
          { state, event ->
            CombatState.PlayerReact(context = state.context, telegraphedHit = event.hitType)
          }
        on<CombatEvent.FightWon>() transitionWith { _, event -> CombatState.Won(event.reason) }
        on<CombatEvent.FightLost>() transitionWith { _, event -> CombatState.Lost(event.reason) }
      }

      state<CombatState.PlayerReact> {
        on<CombatEvent.Dodge>() transitionWith { state, _ -> state.react(Reaction.DODGE, random) }
        on<CombatEvent.Block>() transitionWith { state, _ -> state.react(Reaction.BLOCK, random) }
        on<CombatEvent.Invisible>() transitionWith
          { state, _ ->
            state.react(Reaction.INVISIBLE, random)
          }
        on<CombatEvent.Stand>() transitionWith { state, _ -> state.react(Reaction.STAND, random) }
      }

      state<CombatState.EnemyResolve> {
        on<CombatEvent.RoundContinues>() transitionWith
          { state, _ ->
            CombatState.PlayerTurn(context = state.context.copy(specialLockout = false))
          }
        on<CombatEvent.FightLost>() transitionWith { _, event -> CombatState.Lost(event.reason) }
      }

      state<CombatState.Won> {}
      state<CombatState.Lost> {}
    }

  machine.withEffects(coroutineScope) {
    onEnter<CombatState.EnemyTelegraph>() effect
      { state ->
        resolveTelegraph(state, random, emojiSink)
      }
    onEnter<CombatState.EnemyResolve>() effect
      { state ->
        resolveEnemyRound(state, random, emojiSink)
      }
  }

  return machine
}

private enum class Reaction {
  DODGE,
  BLOCK,
  INVISIBLE,
  STAND,
}

private fun IntRange.roll(random: Random): Int = random.nextInt(first, last + 1)

private fun CombatContext.dealDamageToMonster(damage: Int): CombatContext =
  copy(monsterHp = (monsterHp - damage).coerceAtLeast(0))

private fun CombatContext.heal(random: Random): CombatContext =
  if (playerHasHealed) {
    this
  } else {
    copy(
      playerHp = (playerHp + HEAL_RANGE.roll(random)).coerceAtMost(PLAYER_STARTING_HP),
      playerHasHealed = true,
    )
  }

/**
 * Resolves a player's reaction to the telegraphed hit. A [Reaction.DODGE]/[Reaction.BLOCK] chosen
 * while [CombatContext.specialLockout] is active behaves as [Reaction.STAND] instead of failing —
 * per KSM's "unhandled input is silently absorbed, never a runtime error" convention.
 */
private fun CombatState.PlayerReact.react(
  reaction: Reaction,
  random: Random,
): CombatState.EnemyResolve {
  val locked = context.specialLockout && (reaction == Reaction.DODGE || reaction == Reaction.BLOCK)
  val alreadyInvisible = context.playerHasInvisibility
  val effective =
    when {
      locked -> Reaction.STAND
      reaction == Reaction.INVISIBLE && alreadyInvisible -> Reaction.STAND
      else -> reaction
    }

  val incomingRoll =
    (if (telegraphedHit == HitType.NORMAL) context.enemyType.normalDamageRange
      else context.enemyType.bigHitDamageRange)
      .roll(random)

  val damage =
    when (effective) {
      Reaction.DODGE ->
        if (telegraphedHit == HitType.NORMAL) {
          if (random.nextInt(0, 100) < DODGE_NORMAL_EVADE_CHANCE_PERCENT) 0 else incomingRoll
        } else {
          incomingRoll
        }
      Reaction.BLOCK ->
        if (telegraphedHit == HitType.NORMAL) {
          incomingRoll * BLOCK_NORMAL_DAMAGE_PERCENT / 100
        } else {
          incomingRoll * BLOCK_BIG_DAMAGE_PERCENT / 100
        }
      Reaction.INVISIBLE -> 0
      Reaction.STAND -> incomingRoll
    }

  val usedInvisibility = effective == Reaction.INVISIBLE && !alreadyInvisible
  return CombatState.EnemyResolve(
    context =
      context.copy(
        playerHp = (context.playerHp - damage).coerceAtLeast(0),
        playerHasInvisibility = context.playerHasInvisibility || usedInvisibility,
        specialLockout = false,
      ),
    hitLanded = damage > 0,
  )
}

private suspend fun resolveTelegraph(
  state: CombatState.EnemyTelegraph,
  random: Random,
  emojiSink: MutableSharedFlow<String>?,
): CombatEvent {
  emojiSink?.emit(state.lastPlayerAction.emoji())

  if (state.context.monsterHp <= 0) {
    val counter = FINAL_BLOW_RANGE.roll(random)
    val playerHpAfter = (state.context.playerHp - counter).coerceAtLeast(0)
    emojiSink?.emit("💀")
    return if (playerHpAfter <= 0) {
      CombatEvent.FightLost(Outcome.MUTUAL_KILL)
    } else {
      CombatEvent.FightWon(Outcome.FINAL_BLOW)
    }
  }

  val hitType =
    if (random.nextInt(0, 100) < state.context.enemyType.bigHitChancePercent) HitType.BIG
    else HitType.NORMAL
  emojiSink?.emit(if (hitType == HitType.BIG) "⚠️" else "👊")
  return CombatEvent.TelegraphResolved(hitType)
}

private suspend fun resolveEnemyRound(
  state: CombatState.EnemyResolve,
  random: Random,
  emojiSink: MutableSharedFlow<String>?,
): CombatEvent {
  emojiSink?.emit(if (state.hitLanded) "💢" else "💨")

  if (state.context.playerHp <= 0) {
    val counter = FINAL_BLOW_RANGE.roll(random)
    val monsterHpAfter = (state.context.monsterHp - counter).coerceAtLeast(0)
    emojiSink?.emit("☠️")
    return if (monsterHpAfter <= 0) {
      CombatEvent.FightLost(Outcome.MUTUAL_KILL)
    } else {
      CombatEvent.FightLost(Outcome.FINAL_BLOW)
    }
  }

  return CombatEvent.RoundContinues
}

private fun PlayerAction.emoji(): String =
  when (this) {
    PlayerAction.ATTACK -> "⚔️"
    PlayerAction.DEFEND -> "💚"
    PlayerAction.SPECIAL_MOVE -> "✨"
  }
