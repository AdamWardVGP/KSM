package dev.adamwardvgp.sample.adventure

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import coffee.adammakes.ksm.StateMachine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CombatStateMachineTest {

  private val goblin =
    EnemyType(
      displayName = "Test Goblin",
      maxHp = 20,
      normalDamageRange = 10..10,
      bigHitDamageRange = 16..16,
      bigHitChancePercent = 0,
    )

  /** Returns fixed values in call order, ignoring the RNG's actual bit stream entirely. */
  private class ScriptedRandom(private val values: MutableList<Int>) : Random() {
    override fun nextBits(bitCount: Int): Int = error("unused by CombatStateMachine")

    override fun nextInt(from: Int, until: Int): Int = values.removeAt(0)
  }

  /** Dispatches a PlayerTurn action and consumes the transient EnemyTelegraph in between. */
  private suspend fun ReceiveTurbine<CombatState>.playerTurn(
    machine: StateMachine<CombatState, CombatEvent>,
    event: CombatEvent,
  ): CombatState.PlayerReact {
    machine.dispatchEvent(event)
    awaitItem() // EnemyTelegraph
    return awaitItem() as CombatState.PlayerReact
  }

  /** Dispatches a PlayerReact reaction and consumes the transient EnemyResolve in between. */
  private suspend fun ReceiveTurbine<CombatState>.playerReact(
    machine: StateMachine<CombatState, CombatEvent>,
    event: CombatEvent,
  ): CombatState {
    machine.dispatchEvent(event)
    awaitItem() // EnemyResolve
    return awaitItem()
  }

  @Test
  fun `Attack damages the monster and the round loops back to a fresh PlayerTurn`() = runTest {
    // Attack roll (8..12) -> 9; telegraph roll (0..99) -> 50 (>=0 bigHitChancePercent, so Normal);
    // Stand roll for the monster's Normal hit is fixed by goblin's damage range (10).
    val random = ScriptedRandom(mutableListOf(9, 50, 10))
    val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = goblin)

    machine.currentState.test {
      assertEquals(
        CombatState.PlayerTurn(CombatContext(playerHp = 20, monsterHp = 20, enemyType = goblin)),
        awaitItem(),
      )

      val react = playerTurn(machine, CombatEvent.Attack)
      assertEquals(11, react.context.monsterHp)
      assertEquals(HitType.NORMAL, react.telegraphedHit)

      val next = playerReact(machine, CombatEvent.Stand) as CombatState.PlayerTurn
      assertEquals(11, next.context.monsterHp)
      assertEquals(10, next.context.playerHp)
      assertTrue(!next.context.specialLockout)
    }
  }

  @Test
  fun `Defend heals once then is a no-op on a second use`() = runTest {
    val random =
      ScriptedRandom(
        mutableListOf(
          5, // Attack damage roll (monster HP only, irrelevant here)
          50, // telegraph roll -> Normal
          6, // round 1 Stand incoming damage -> playerHp 20 -> 14
          10, // Defend heal roll -> playerHp 14 -> 24, clamped to 20
          50, // telegraph roll -> Normal
          6, // round 2 Stand incoming damage -> playerHp 20 -> 14
          50, // telegraph roll -> Normal
        )
      )
    val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = goblin)

    machine.currentState.test {
      awaitItem() // initial PlayerTurn
      playerTurn(machine, CombatEvent.Attack)
      playerReact(machine, CombatEvent.Stand) // PlayerTurn, HP 14

      val healed = playerTurn(machine, CombatEvent.Defend)
      assertEquals(20, healed.context.playerHp) // healed 10, clamped to starting HP
      assertTrue(healed.context.playerHasHealed)
      playerReact(machine, CombatEvent.Stand) // PlayerTurn, HP 14 again

      val second = playerTurn(machine, CombatEvent.Defend)
      assertEquals(14, second.context.playerHp) // no heal fired the second time
      assertTrue(second.context.playerHasHealed)
    }
  }

  @Test
  fun `SpecialMove locks out Dodge and Block on the following reaction`() = runTest {
    val random = ScriptedRandom(mutableListOf(10, 50, 10))
    val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = goblin)

    machine.currentState.test {
      awaitItem() // initial PlayerTurn

      val react = playerTurn(machine, CombatEvent.SpecialMove)
      assertTrue(react.context.specialLockout)

      // Dodge while locked out behaves like Stand: the roll is spent, full damage lands.
      val after = playerReact(machine, CombatEvent.Dodge) as CombatState.PlayerTurn
      assertEquals(10, after.context.playerHp)
      assertTrue(!after.context.specialLockout)
    }
  }

  @Test
  fun `Dodge fully evades a Normal hit on the winning roll`() = runTest {
    val random = ScriptedRandom(mutableListOf(10, 50, 10, 0))
    val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = goblin)

    machine.currentState.test {
      awaitItem()
      playerTurn(machine, CombatEvent.Attack)
      val after = playerReact(machine, CombatEvent.Dodge) as CombatState.PlayerTurn
      assertEquals(20, after.context.playerHp)
    }
  }

  @Test
  fun `Dodge fails a Normal hit on the losing roll and takes full damage`() = runTest {
    val random = ScriptedRandom(mutableListOf(10, 50, 10, 99))
    val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = goblin)

    machine.currentState.test {
      awaitItem()
      playerTurn(machine, CombatEvent.Attack)
      val after = playerReact(machine, CombatEvent.Dodge) as CombatState.PlayerTurn
      assertEquals(10, after.context.playerHp)
    }
  }

  @Test
  fun `Dodge always fails a Big hit`() = runTest {
    val bigHitAlways = goblin.copy(bigHitChancePercent = 100)
    val random = ScriptedRandom(mutableListOf(10, 50, 16))
    val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = bigHitAlways)

    machine.currentState.test {
      awaitItem()
      val react = playerTurn(machine, CombatEvent.Attack)
      assertEquals(HitType.BIG, react.telegraphedHit)
      val after = playerReact(machine, CombatEvent.Dodge) as CombatState.PlayerTurn
      assertEquals(4, after.context.playerHp)
    }
  }

  @Test
  fun `Block halves a Normal hit and quarters mitigation on a Big hit`() = runTest {
    val random = ScriptedRandom(mutableListOf(10, 50, 10))
    val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = goblin)

    machine.currentState.test {
      awaitItem()
      playerTurn(machine, CombatEvent.Attack)
      val after = playerReact(machine, CombatEvent.Block) as CombatState.PlayerTurn
      assertEquals(15, after.context.playerHp) // 20 - (10 / 2)
    }
  }

  @Test
  fun `Block vs a Big hit only mitigates a quarter of the damage`() = runTest {
    val bigHitAlways = goblin.copy(bigHitChancePercent = 100)
    val random = ScriptedRandom(mutableListOf(10, 50, 16))
    val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = bigHitAlways)

    machine.currentState.test {
      awaitItem()
      playerTurn(machine, CombatEvent.Attack)
      val after = playerReact(machine, CombatEvent.Block) as CombatState.PlayerTurn
      assertEquals(8, after.context.playerHp) // 20 - (16 * 75 / 100) = 20 - 12
    }
  }

  @Test
  fun `Invisible always evades but only once`() = runTest {
    val random = ScriptedRandom(mutableListOf(10, 50, 10, 5, 50, 10))
    val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = goblin)

    machine.currentState.test {
      awaitItem()
      playerTurn(machine, CombatEvent.Attack)
      val first = playerReact(machine, CombatEvent.Invisible) as CombatState.PlayerTurn
      assertEquals(20, first.context.playerHp) // fully evaded, incoming roll wasted
      assertTrue(first.context.playerHasInvisibility)

      playerTurn(machine, CombatEvent.Attack)
      val second = playerReact(machine, CombatEvent.Invisible) as CombatState.PlayerTurn
      assertEquals(10, second.context.playerHp) // second Invisible behaves as Stand
    }
  }

  @Test
  fun `Stand always takes full damage and spends no resource`() = runTest {
    val random = ScriptedRandom(mutableListOf(10, 50, 10))
    val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = goblin)

    machine.currentState.test {
      awaitItem()
      playerTurn(machine, CombatEvent.Attack)
      val after = playerReact(machine, CombatEvent.Stand) as CombatState.PlayerTurn
      assertEquals(10, after.context.playerHp)
      assertTrue(!after.context.playerHasHealed)
      assertTrue(!after.context.playerHasInvisibility)
    }
  }

  @Test
  fun `killing the monster triggers its automatic final blow, then Won if the player survives`() =
    runTest {
      // Attack roll high enough to kill a 20 HP monster in one hit; final-blow roll (5..15) -> 5.
      val random = ScriptedRandom(mutableListOf(25, 5))
      val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = goblin)

      machine.currentState.test {
        awaitItem()
        machine.dispatchEvent(CombatEvent.SpecialMove)
        awaitItem() // EnemyTelegraph
        val won = awaitItem() as CombatState.Won
        assertEquals(Outcome.FINAL_BLOW, won.reason)
      }
    }

  @Test
  fun `killing the monster with a final blow that also kills the player resolves as a mutual kill`() =
    runTest {
      val random = ScriptedRandom(mutableListOf(25, 20))
      val machine = getCombatStateMachine(backgroundScope, random = random, enemyType = goblin)

      machine.currentState.test {
        val initial = awaitItem() as CombatState.PlayerTurn
        assertEquals(20, initial.context.playerHp)
        machine.dispatchEvent(CombatEvent.SpecialMove)
        awaitItem() // EnemyTelegraph
        val lost = awaitItem() as CombatState.Lost
        assertEquals(Outcome.MUTUAL_KILL, lost.reason)
      }
    }

  @Test
  fun `an enemy hit that would kill the player triggers the player's automatic final blow, then Lost if the monster survives`() =
    runTest {
      // Attack roll 5 (monster survives at 15); telegraph roll -> Normal; Stand roll 20 kills
      // the player outright; final-blow counter roll 5 leaves the monster alive.
      val random = ScriptedRandom(mutableListOf(5, 50, 20, 5))
      val lethalGoblin = goblin.copy(normalDamageRange = 20..20)
      val machine =
        getCombatStateMachine(backgroundScope, random = random, enemyType = lethalGoblin)

      machine.currentState.test {
        awaitItem()
        playerTurn(machine, CombatEvent.Attack)
        machine.dispatchEvent(CombatEvent.Stand)
        awaitItem() // EnemyResolve
        val lost = awaitItem() as CombatState.Lost
        assertEquals(Outcome.FINAL_BLOW, lost.reason)
      }
    }

  @Test
  fun `player death with a final blow that also kills the monster resolves as a mutual kill`() =
    runTest {
      val random = ScriptedRandom(mutableListOf(5, 50, 20, 15))
      val lethalGoblin = goblin.copy(normalDamageRange = 20..20)
      val machine =
        getCombatStateMachine(backgroundScope, random = random, enemyType = lethalGoblin)

      machine.currentState.test {
        awaitItem()
        playerTurn(machine, CombatEvent.Attack)
        machine.dispatchEvent(CombatEvent.Stand)
        awaitItem() // EnemyResolve
        val lost = awaitItem() as CombatState.Lost
        assertEquals(Outcome.MUTUAL_KILL, lost.reason)
      }
    }
}
