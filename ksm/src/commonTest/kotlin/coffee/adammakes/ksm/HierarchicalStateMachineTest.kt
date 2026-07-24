package coffee.adammakes.ksm

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class HierarchicalStateMachineTest {

  sealed interface TestState

  data object Idle : TestState

  sealed interface Active : TestState

  sealed interface Moving : Active

  data class Running(val step: Int = 0) : Moving

  data object Walking : Moving

  data object Paused : Active

  data object Stopped : TestState

  sealed interface FirstParent : TestState

  sealed interface SecondParent : TestState

  data object SharedChild : FirstParent, SecondParent

  sealed interface TestEvent

  data object Pause : TestEvent

  data object Stop : TestEvent

  data object Tick : TestEvent

  @Test
  fun `child inherits transitions from its nearest declared parent`() = runTest {
    val machine =
      stateMachine<TestState, TestEvent> {
        initialState = Running()
        dispatchedOn = backgroundScope

        state<Active> {
          on<Stop>() transitionTo Stopped

          state<Moving> {
            on<Pause>() transitionTo Paused

            state<Running> {}
            state<Walking> {}
          }

          state<Paused> {}
        }
      }

    machine.currentState.test {
      assertEquals(Running(), awaitItem())

      machine.dispatchEvent(Pause)

      assertEquals(Paused, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `child transition specializes the same parent event`() = runTest {
    val machine =
      stateMachine<TestState, TestEvent> {
        initialState = Running()
        dispatchedOn = backgroundScope

        state<Active> {
          on<Stop>() transitionTo Stopped

          state<Moving> {
            state<Running> { on<Stop>() transitionTo Paused }
            state<Walking> {}
          }

          state<Paused> {}
        }
      }

    machine.currentState.test {
      assertEquals(Running(), awaitItem())

      machine.dispatchEvent(Stop)

      assertEquals(Paused, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `nearest parent transition wins`() = runTest {
    val machine =
      stateMachine<TestState, TestEvent> {
        initialState = Walking
        dispatchedOn = backgroundScope

        state<Active> {
          on<Stop>() transitionTo Stopped

          state<Moving> {
            on<Stop>() transitionTo Paused
            state<Walking> {}
          }

          state<Paused> {}
        }
      }

    machine.currentState.test {
      assertEquals(Walking, awaitItem())

      machine.dispatchEvent(Stop)

      assertEquals(Paused, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `state path is ordered from outer parent to concrete leaf`() = runTest {
    val machine =
      stateMachine<TestState, TestEvent> {
        initialState = Running()
        dispatchedOn = backgroundScope

        state<Active> {
          state<Moving> {
            state<Running> {}
            state<Walking> {}
          }
        }
      }

    assertEquals(listOf(Active::class, Moving::class, Running::class), machine.statePath(Running()))
  }

  @Test
  fun `same leaf type transition preserves state payloads`() = runTest {
    val machine =
      stateMachine<TestState, TestEvent> {
        initialState = Running()
        dispatchedOn = backgroundScope

        state<Active> {
          state<Moving> {
            state<Running> {
              on<Tick>() transitionWith { state, _ -> state.copy(step = state.step + 1) }
            }
          }
        }
      }

    machine.currentState.test {
      assertEquals(Running(), awaitItem())

      machine.dispatchEvent(Tick)

      assertEquals(Running(step = 1), awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `undeclared Kotlin supertype does not receive events`() = runTest {
    val machine =
      stateMachine<TestState, TestEvent> {
        initialState = Running()
        dispatchedOn = backgroundScope

        state<Active> { on<Stop>() transitionTo Stopped }
      }

    machine.dispatchEvent(Stop)
    advanceUntilIdle()

    assertEquals(Running(), machine.currentState.value)
  }

  @Test
  fun `state cannot be declared beneath multiple parents`() = runTest {
    assertFailsWith<IllegalArgumentException> {
      stateMachine<TestState, TestEvent> {
        initialState = SharedChild
        dispatchedOn = backgroundScope

        state<FirstParent> { state<SharedChild> {} }
        state<SecondParent> { state<SharedChild> {} }
      }
    }
  }

  @Test
  fun `flat state definitions remain supported`() = runTest {
    val machine =
      stateMachine<TestState, TestEvent> {
        initialState = Idle
        dispatchedOn = backgroundScope

        state<Idle> { on<Stop>() transitionTo Stopped }
      }

    machine.currentState.test {
      assertEquals(Idle, awaitItem())

      machine.dispatchEvent(Stop)

      assertEquals(Stopped, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }
}
