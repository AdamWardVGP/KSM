package coffee.adammakes.ksm

import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CompositeStateMachineTest {

  sealed interface ParentState

  data object Idle : ParentState

  data object UpdateFlow : ParentState

  data object Done : ParentState

  sealed interface ParentEvent

  data object StartUpdate : ParentEvent

  data object UpdateFinished : ParentEvent

  data object Cancel : ParentEvent

  sealed interface ChildState

  data object Checking : ChildState

  data object Downloading : ChildState

  data object ChildDone : ChildState

  sealed interface ChildEvent

  data object DownloadStarted : ChildEvent

  data object DownloadComplete : ChildEvent

  private fun buildChild(scope: kotlinx.coroutines.CoroutineScope) =
    stateMachine<ChildState, ChildEvent> {
      initialState = Checking
      dispatchedOn = scope

      state<Checking> { on<DownloadStarted>() transitionTo Downloading }
      state<Downloading> { on<DownloadComplete>() transitionTo ChildDone }
      state<ChildDone> {}
    }

  @Test
  fun `entering composite state creates a fresh child starting at its own initialState`() =
    runTest {
      lateinit var handle: CompositeHandle<ParentEvent, ChildEvent, ChildState>
      val machine =
        stateMachine<ParentState, ParentEvent> {
          initialState = Idle
          dispatchedOn = backgroundScope

          state<Idle> { on<StartUpdate>() transitionTo UpdateFlow }
          state<UpdateFlow> { handle = child(factory = { buildChild(backgroundScope) }) }
          state<Done> {}
        }

      handle.activeChildState.test {
        assertNull(awaitItem())
        machine.dispatchEvent(StartUpdate)
        assertEquals(Checking, awaitItem())
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `two separate entries into composite state produce independent child instances`() = runTest {
    lateinit var handle: CompositeHandle<ParentEvent, ChildEvent, ChildState>
    val machine =
      stateMachine<ParentState, ParentEvent> {
        initialState = Idle
        dispatchedOn = backgroundScope

        state<Idle> { on<StartUpdate>() transitionTo UpdateFlow }
        state<UpdateFlow> {
          on<UpdateFinished>() transitionTo Idle
          handle = child(factory = { buildChild(backgroundScope) })
        }
      }

    handle.activeChildState.test {
      assertNull(awaitItem())

      machine.dispatchEvent(StartUpdate)
      assertEquals(Checking, awaitItem())

      handle.dispatch(DownloadStarted)
      assertEquals(Downloading, awaitItem())

      machine.dispatchEvent(UpdateFinished)
      assertNull(awaitItem())

      machine.dispatchEvent(StartUpdate)
      assertEquals(Checking, awaitItem())

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `child event routes to active child`() = runTest {
    lateinit var handle: CompositeHandle<ParentEvent, ChildEvent, ChildState>
    val machine =
      stateMachine<ParentState, ParentEvent> {
        initialState = Idle
        dispatchedOn = backgroundScope

        state<Idle> { on<StartUpdate>() transitionTo UpdateFlow }
        state<UpdateFlow> { handle = child(factory = { buildChild(backgroundScope) }) }
      }

    handle.activeChildState.test {
      assertNull(awaitItem())
      machine.dispatchEvent(StartUpdate)
      assertEquals(Checking, awaitItem())

      handle.dispatch(DownloadStarted)
      assertEquals(Downloading, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `child event is silently dropped when composite is not active`() = runTest {
    lateinit var handle: CompositeHandle<ParentEvent, ChildEvent, ChildState>
    val machine =
      stateMachine<ParentState, ParentEvent> {
        initialState = Idle
        dispatchedOn = backgroundScope

        state<Idle> { on<StartUpdate>() transitionTo UpdateFlow }
        state<UpdateFlow> { handle = child(factory = { buildChild(backgroundScope) }) }
      }

    machine.currentState.test {
      assertEquals(Idle, awaitItem())

      // Composite never entered — this child event has nowhere to go.
      handle.dispatch(DownloadStarted)

      cancelAndIgnoreRemainingEvents()
    }

    assertNull(handle.activeChildState.value)
    assertEquals(Idle, machine.currentState.value)
  }

  @Test
  fun `parent event unhandled by the composite state is dropped, child untouched`() = runTest {
    lateinit var handle: CompositeHandle<ParentEvent, ChildEvent, ChildState>
    val machine =
      stateMachine<ParentState, ParentEvent> {
        initialState = Idle
        dispatchedOn = backgroundScope

        state<Idle> { on<StartUpdate>() transitionTo UpdateFlow }
        // UpdateFlow declares no handler for Cancel — composite has nothing to bypass into.
        state<UpdateFlow> { handle = child(factory = { buildChild(backgroundScope) }) }
      }

    machine.currentState.test {
      assertEquals(Idle, awaitItem())
      machine.dispatchEvent(StartUpdate)
      assertEquals(UpdateFlow, awaitItem())

      handle.activeChildState.test {
        // Composite already entered by the time this collector subscribes, so the current
        // value (not null) is the first emission.
        assertEquals(Checking, awaitItem())

        handle.dispatch(Cancel)
        expectNoEvents()

        cancelAndIgnoreRemainingEvents()
      }

      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }

    assertEquals(UpdateFlow, machine.currentState.value)
    assertEquals(Checking, handle.activeChildState.value)
  }

  sealed interface MixedState

  sealed interface Active : MixedState

  data object Running : Active

  data object Paused : Active

  data object CompositeHost : MixedState

  sealed interface MixedEvent

  data object Pause : MixedEvent

  @Test
  fun `existing same-typed hierarchical bubbling is unaffected by an unrelated composite state`() =
    runTest {
      lateinit var handle: CompositeHandle<MixedEvent, ChildEvent, ChildState>
      val machine =
        stateMachine<MixedState, MixedEvent> {
          initialState = Running
          dispatchedOn = backgroundScope

          state<Active> {
            on<Pause>() transitionTo Paused
            state<Running> {}
            state<Paused> {}
          }
          state<CompositeHost> { handle = child(factory = { buildChild(backgroundScope) }) }
        }

      // Running has no own Pause handler — must bubble to its declared parent Active,
      // exactly as it does without any composite state present in the graph.
      machine.currentState.test {
        assertEquals(Running, awaitItem())
        machine.dispatchEvent(Pause)
        assertEquals(Paused, awaitItem())
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `parent event handled by composite's own transition bypasses child regardless of child state`() =
    runTest {
      lateinit var handle: CompositeHandle<ParentEvent, ChildEvent, ChildState>
      val machine =
        stateMachine<ParentState, ParentEvent> {
          initialState = Idle
          dispatchedOn = backgroundScope

          state<Idle> { on<StartUpdate>() transitionTo UpdateFlow }
          state<UpdateFlow> {
            on<Cancel>() transitionTo Idle
            handle = child(factory = { buildChild(backgroundScope) })
          }
        }

      machine.currentState.test {
        assertEquals(Idle, awaitItem())

        handle.activeChildState.test {
          assertNull(awaitItem())
          machine.dispatchEvent(StartUpdate)
          assertEquals(Checking, awaitItem())

          handle.dispatch(DownloadStarted)
          assertEquals(Downloading, awaitItem())

          handle.dispatch(Cancel)
          assertNull(awaitItem())
          cancelAndIgnoreRemainingEvents()
        }

        assertEquals(UpdateFlow, awaitItem())
        assertEquals(Idle, awaitItem())
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `exit wiring auto-dispatches mapped parent event when child reaches wired state`() = runTest {
    lateinit var handle: CompositeHandle<ParentEvent, ChildEvent, ChildState>
    val machine =
      stateMachine<ParentState, ParentEvent> {
        initialState = Idle
        dispatchedOn = backgroundScope

        state<Idle> { on<StartUpdate>() transitionTo UpdateFlow }
        state<UpdateFlow> {
          on<UpdateFinished>() transitionTo Done
          handle =
            child(factory = { buildChild(backgroundScope) }) { exit<ChildDone> { UpdateFinished } }
        }
        state<Done> {}
      }

    machine.currentState.test {
      assertEquals(Idle, awaitItem())
      machine.dispatchEvent(StartUpdate)
      assertEquals(UpdateFlow, awaitItem())

      handle.dispatch(DownloadStarted)
      handle.dispatch(DownloadComplete)

      assertEquals(Done, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `exit wiring can target a non-terminal child state`() = runTest {
    lateinit var handle: CompositeHandle<ParentEvent, ChildEvent, ChildState>
    val machine =
      stateMachine<ParentState, ParentEvent> {
        initialState = Idle
        dispatchedOn = backgroundScope

        state<Idle> { on<StartUpdate>() transitionTo UpdateFlow }
        state<UpdateFlow> {
          on<UpdateFinished>() transitionTo Done
          handle =
            child(factory = { buildChild(backgroundScope) }) {
              exit<Downloading> { UpdateFinished }
            }
        }
        state<Done> {}
      }

    machine.currentState.test {
      assertEquals(Idle, awaitItem())
      machine.dispatchEvent(StartUpdate)
      assertEquals(UpdateFlow, awaitItem())

      handle.dispatch(DownloadStarted)

      assertEquals(Done, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }
}
