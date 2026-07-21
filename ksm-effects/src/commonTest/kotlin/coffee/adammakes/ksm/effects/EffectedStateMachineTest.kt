package coffee.adammakes.ksm.effects

import app.cash.turbine.test
import coffee.adammakes.ksm.stateMachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
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

  sealed interface Session : State

  data class First(val version: Int = 0) : Session

  data object Second : Session

  data object Done : State

  data object EnterSession : Event

  data object Next : Event

  data object ExitSession : Event

  data object Refresh : Event

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

  private fun hierarchicalMachine(scope: CoroutineScope, initial: State = Idle) =
    stateMachine<State, Event> {
      initialState = initial
      dispatchedOn = scope

      state<Idle> { on<EnterSession>() transitionTo First() }

      state<Session> {
        on<ExitSession>() transitionTo Done

        state<First> {
          on<Next>() transitionTo Second
          on<Refresh>() transitionWith { state, _ -> state.copy(version = state.version + 1) }
        }

        state<Second> {}
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

  @Test
  fun `parent effect survives sibling transition while child effect is replaced`() =
    testScope.runTest {
      val parentStart = CompletableDeferred<Unit>()
      val firstStart = CompletableDeferred<Unit>()
      val firstCancellation = CompletableDeferred<Unit>()
      val parentCancellation = CompletableDeferred<Unit>()
      val secondStart = CompletableDeferred<Unit>()
      val secondCancellation = CompletableDeferred<Unit>()

      val fsm = hierarchicalMachine(backgroundScope)
      val effected =
        fsm.withEffects(backgroundScope) {
          onEnter<Session>() effect
            {
              parentStart.complete(Unit)
              try {
                awaitCancellation()
              } finally {
                parentCancellation.complete(Unit)
              }
            }
          onEnter<First>() effect
            {
              firstStart.complete(Unit)
              try {
                awaitCancellation()
              } finally {
                firstCancellation.complete(Unit)
              }
            }
          onEnter<Second>() effect
            {
              secondStart.complete(Unit)
              try {
                awaitCancellation()
              } finally {
                secondCancellation.complete(Unit)
              }
            }
        }

      effected.currentState.test {
        assertEquals(Idle, awaitItem())

        effected.dispatchEvent(EnterSession)
        assertEquals(First(), awaitItem())
        parentStart.await()
        firstStart.await()

        effected.dispatchEvent(Next)
        assertEquals(Second, awaitItem())
        firstCancellation.await()
        secondStart.await()
        assertFalse(parentCancellation.isCompleted)

        effected.dispatchEvent(ExitSession)
        assertEquals(Done, awaitItem())
        parentCancellation.await()
        secondCancellation.await()

        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `same leaf class transition restarts leaf effect but preserves parent effect`() =
    testScope.runTest {
      var parentStarted = 0
      var leafStarted = 0
      var leafCancelled = 0
      val parentStart = CompletableDeferred<Unit>()
      val firstLeafStart = CompletableDeferred<Unit>()
      val secondLeafStart = CompletableDeferred<Unit>()
      val firstLeafCancellation = CompletableDeferred<Unit>()

      val fsm = hierarchicalMachine(backgroundScope, initial = First())
      val effected =
        fsm.withEffects(backgroundScope) {
          onEnter<Session>() effect
            {
              parentStarted += 1
              parentStart.complete(Unit)
              awaitCancellation()
            }
          onEnter<First>() effect
            {
              leafStarted += 1
              if (leafStarted == 1) {
                firstLeafStart.complete(Unit)
              } else {
                secondLeafStart.complete(Unit)
              }
              try {
                awaitCancellation()
              } finally {
                leafCancelled += 1
                firstLeafCancellation.complete(Unit)
              }
            }
        }

      effected.currentState.test {
        assertEquals(First(), awaitItem())
        parentStart.await()
        firstLeafStart.await()
        assertEquals(1, parentStarted)
        assertEquals(1, leafStarted)

        effected.dispatchEvent(Refresh)
        assertEquals(First(version = 1), awaitItem())
        firstLeafCancellation.await()
        secondLeafStart.await()

        assertEquals(1, parentStarted)
        assertEquals(2, leafStarted)
        assertEquals(1, leafCancelled)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `parent effect result is dispatched to machine`() =
    testScope.runTest {
      val fsm = hierarchicalMachine(backgroundScope, initial = First())
      val effected =
        fsm.withEffects(backgroundScope) {
          onEnter<Session>() effect { ExitSession }
          onEnter<First>() effect { awaitCancellation() }
        }

      effected.currentState.test {
        assertEquals(First(), awaitItem())
        assertEquals(Done, awaitItem())
        cancelAndIgnoreRemainingEvents()
      }
    }
}
