package coffee.adammakes.ksm.effects

import app.cash.turbine.test
import coffee.adammakes.ksm.stateMachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EffectedStateMachineTest {

  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  sealed interface State

  data object Idle : State

  data object Active : State

  sealed interface Event

  data object Activate : Event

  data object Deactivate : Event

  data object WorkDone : Event

  private fun machine(scope: CoroutineScope) =
    stateMachine<State, Event> {
      initialState = Idle
      dispatchedOn = scope

      state<Idle> { on<Activate>() transitionTo Active }

      state<Active> {
        on<Deactivate>() transitionTo Idle
        on<WorkDone>() transitionTo Idle
      }
    }

  @Test
  fun `effect result is dispatched to machine`() =
    testScope.runTest {
      val fsm = machine(backgroundScope)
      val effected = fsm.withEffects(backgroundScope) { onEnter<Active>() effect { WorkDone } }

      effected.currentState.test {
        assertEquals(Idle, awaitItem())

        effected.dispatchEvent(Activate)
        assertEquals(Active, awaitItem())
        assertEquals(Idle, awaitItem())

        cancelAndIgnoreRemainingEvents()
      }

      backgroundScope.cancel()
    }

  @Test
  fun `registering a second effect for the same state throws`() =
    testScope.runTest {
      val scope = CoroutineScope(coroutineContext + SupervisorJob())
      val fsm = machine(scope)

      assertFailsWith<IllegalStateException> {
        fsm.withEffects(scope) {
          onEnter<Active>() effect { _ -> CompletableDeferred<Event>().await() }
          onEnter<Active>() effect { _ -> CompletableDeferred<Event>().await() }
        }
      }

      scope.cancel()
    }

  @Test
  fun `no effect runs for states with empty contributor output`() =
    testScope.runTest {
      var effectCalled = false

      val fsm = machine(backgroundScope)
      val effected =
        fsm.withEffects(backgroundScope) {
          onEnter<Active>() effect
            {
              effectCalled = true
              WorkDone
            }
        }

      // stays Idle — no effects registered for Idle
      advanceUntilIdle()
      assertEquals(false, effectCalled)

      backgroundScope.cancel()
    }

  @Test
  fun `previous state effects cancelled on transition`() =
    testScope.runTest {
      val scope = CoroutineScope(coroutineContext + SupervisorJob())
      val neverCompletes = CompletableDeferred<Event>()
      var effectStarted = false

      val fsm = machine(scope)
      val effected =
        fsm.withEffects(scope) {
          onEnter<Active>() effect
            { _ ->
              effectStarted = true
              neverCompletes.await()
            }
        }

      effected.currentState.test {
        assertEquals(Idle, awaitItem())

        effected.dispatchEvent(Activate)
        assertEquals(Active, awaitItem())

        advanceUntilIdle()
        assertEquals(true, effectStarted)

        // Deactivate cancels the Active stateJob
        effected.dispatchEvent(Deactivate)
        assertEquals(Idle, awaitItem())

        cancelAndIgnoreRemainingEvents()
      }

      scope.cancel()
    }
}
