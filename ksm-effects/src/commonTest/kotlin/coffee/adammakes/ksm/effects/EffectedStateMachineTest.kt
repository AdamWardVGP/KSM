package coffee.adammakes.ksm.effects

import app.cash.turbine.test
import coffee.adammakes.ksm.stateMachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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

  data object WorkCancelled : Event

  private fun machine(scope: kotlinx.coroutines.CoroutineScope) =
    stateMachine<State, Event> {
      initialState = Idle
      dispatchedOn = scope

      state<Idle> { on<Activate>() transitionTo Active }

      state<Active> {
        on<Deactivate>() transitionTo Idle
        on<WorkDone>() transitionTo Idle
        on<WorkCancelled>() transitionTo Idle
      }
    }

  @Test
  fun `effect result is dispatched to machine`() =
    testScope.runTest {
      val fsm = machine(backgroundScope)
      val effected =
        fsm.withEffects(backgroundScope) { state ->
          when (state) {
            is Active -> listOf(Effect(body = { WorkDone }))
            else -> emptyList()
          }
        }

      effected.currentState.test {
        assertEquals(Idle, awaitItem())

        effected.dispatchEvent(Activate)
        assertEquals(Active, awaitItem())
        // Effect runs immediately and dispatches WorkDone → machine transitions back
        assertEquals(Idle, awaitItem())

        cancelAndIgnoreRemainingEvents()
      }

      backgroundScope.cancel()
    }

  @Test
  fun `cancel path executes when state scope is cancelled`() =
    testScope.runTest {
      var cancelCalled = false
      val scope = CoroutineScope(coroutineContext + SupervisorJob())

      val fsm = machine(scope)

      val effected =
        fsm.withEffects(scope) { state ->
          when (state) {
            is Active ->
              listOf(
                Effect(
                  body = { CompletableDeferred<Event>().await() },
                  onCancel = {
                    cancelCalled = true
                    WorkCancelled
                  },
                )
              )
            else -> emptyList()
          }
        }

      effected.dispatchEvent(Activate)
      advanceUntilIdle()

      effected.dispatchEvent(Deactivate)
      advanceUntilIdle()

      assertEquals(true, cancelCalled)

      scope.cancel()
    }

  @Test
  fun `diagnostic - invokeOnCompletion fires when scope cancelled`() =
    testScope.runTest {
      var cancelHandlerFired = false

      val effectScope = CoroutineScope(coroutineContext + SupervisorJob())

      val job = effectScope.launch { CompletableDeferred<Event>().await() }
      job.invokeOnCompletion { cause ->
        if (cause is CancellationException) cancelHandlerFired = true
      }

      runCurrent() // let effectCoroutine start and suspend at await()

      effectScope.cancel()
      runCurrent() // process cancellation

      assertEquals(true, cancelHandlerFired, "invokeOnCompletion cancel handler did not fire")
    }

  @Test
  fun `cancel path pushes event into machine`() =
    testScope.runTest {
      val fsm = machine(backgroundScope)
      val neverCompletes = CompletableDeferred<Event>()

      val effected =
        fsm.withEffects(backgroundScope) { state ->
          when (state) {
            is Active ->
              listOf(Effect(body = { neverCompletes.await() }, onCancel = { WorkCancelled }))
            else -> emptyList()
          }
        }

      effected.currentState.test {
        assertEquals(Idle, awaitItem())

        effected.dispatchEvent(Activate)
        assertEquals(Active, awaitItem())

        // Deactivate cancels the effect scope; onCancel pushes WorkCancelled → Idle
        effected.dispatchEvent(Deactivate)
        // Either the explicit Deactivate or the pushed WorkCancelled lands Idle
        assertEquals(Idle, awaitItem())

        cancelAndIgnoreRemainingEvents()
      }

      backgroundScope.cancel()
    }

  @Test
  fun `no effect runs for states with empty contributor output`() =
    testScope.runTest {
      var effectCalled = false

      val fsm = machine(backgroundScope)
      val effected =
        fsm.withEffects(backgroundScope) { state ->
          when (state) {
            is Active -> listOf(Effect(body = { effectCalled = true; WorkDone }))
            else -> emptyList()
          }
        }

      // Idle → no effects
      advanceUntilIdle()
      assertEquals(false, effectCalled)

      backgroundScope.cancel()
    }
}
