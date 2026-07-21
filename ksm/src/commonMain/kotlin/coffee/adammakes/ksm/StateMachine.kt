package coffee.adammakes.ksm

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Uniquely identifies a transition by its source state and triggering event classes. */
data class TransitionId(val from: KClass<*>, val event: KClass<*>)

/**
 * Represents a single edge in the state graph: from one state to another, triggered by an event.
 *
 * @param id unique key for this transition
 * @param from the source state class
 * @param to the target state class
 * @param event the event class that triggers this transition
 * @param reduce function that produces the new state from the current state and event
 */
data class Transition<State : Any, Event : Any>(
  val id: TransitionId,
  val from: KClass<out State>,
  val to: KClass<out State>,
  val event: KClass<out Event>,
  val reduce: (State, Event) -> State,
)

/**
 * A finite state machine intended for use in view models. Think of this like a flow-chart for
 * sequences of states, guided by events.
 *
 * A single concrete [State] is persisted and awaits an [Event]. When a relevant [Event] is
 * received, the machine resolves a transition from that state outward through its explicitly
 * declared parents, then emits the resulting [State] via [currentState].
 *
 * States must be declared at most once in the graph; terminal states (no outgoing transitions) are
 * allowed. Unhandled events are silently ignored.
 */
class StateMachine<State : Any, Event : Any>(
  initial: State,
  val transitions: Map<KClass<out State>, Map<KClass<out Event>, Transition<State, Event>>>,
  private val scope: CoroutineScope,
  private val parents: Map<KClass<out State>, KClass<out State>?>,
) {

  constructor(
    initial: State,
    transitions: Map<KClass<out State>, Map<KClass<out Event>, Transition<State, Event>>>,
    scope: CoroutineScope,
  ) : this(initial, transitions, scope, emptyMap())

  private val _currentState = MutableStateFlow(initial)

  /** The current state, as a [StateFlow]. Collect this to observe state changes. */
  public val currentState: StateFlow<State> = _currentState

  // UNLIMITED because:
  // - events are lightweight
  // - backpressure here would block callers, so allow everyone to enqueue
  // - state is single-writer so ordering is preserved
  private val events = Channel<Event>(Channel.UNLIMITED)

  init {
    scope.launch {
      for (event in events) {
        val state = _currentState.value
        val transition = resolveTransition(state, event) ?: continue
        _currentState.value = transition.reduce(state, event)
      }
    }
  }

  /**
   * Enqueues an [event] for processing. If no transition is registered for the current state and
   * this event type, the event is dropped. Events are processed in order on [scope].
   */
  fun dispatchEvent(event: Event) {
    events.trySend(event)
  }

  /**
   * Returns the explicitly declared hierarchy path for [state], ordered from the outermost parent
   * to the concrete state.
   *
   * A state that was not nested beneath a parent has a path containing only its concrete class.
   */
  fun statePath(state: State): List<KClass<out State>> {
    val path = mutableListOf<KClass<out State>>()
    var current: KClass<out State>? = state::class
    while (current != null) {
      path += current
      current = parents[current]
    }
    return path.asReversed()
  }

  private fun resolveTransition(state: State, event: Event): Transition<State, Event>? =
    statePath(state).asReversed().firstNotNullOfOrNull { stateClass ->
      transitions[stateClass]?.get(event::class)
    }
}

/**
 * DSL entry point for building a [StateMachine].
 *
 * Usage:
 * ```kotlin
 * val fsm = stateMachine<AppState, AppEvent> {
 *     initialState = AppState.Idle
 *     dispatchedOn = viewModelScope
 *
 *     state<AppState.Idle> {
 *         on<AppEvent.Start>() transitionTo AppState.Running
 *     }
 * }
 * ```
 */
inline fun <reified State : Any, reified Event : Any> stateMachine(
  block: StateMachineBuilder<State, Event>.() -> Unit
): StateMachine<State, Event> = StateMachineBuilder<State, Event>().apply(block).build()

/** Builder for [StateMachine]. Use via the [stateMachine] DSL function. */
class StateMachineBuilder<State : Any, Event : Any> {

  /** The state the machine starts in. Required. */
  var initialState: State? = null

  /** The [CoroutineScope] used to process events. Required. */
  var dispatchedOn: CoroutineScope? = null

  val transitions =
    mutableMapOf<KClass<out State>, Map<KClass<out Event>, Transition<State, Event>>>()

  @PublishedApi internal val parents = mutableMapOf<KClass<out State>, KClass<out State>?>()

  /** Declares a top-level state and its transitions or nested child states. */
  inline fun <reified STATE : State> state(
    block: StateTransitionBuilder<STATE, State, Event>.() -> Unit
  ) = registerState(parent = null, block)

  @PublishedApi
  internal inline fun <reified STATE : State> registerState(
    parent: KClass<out State>?,
    block: StateTransitionBuilder<STATE, State, Event>.() -> Unit,
  ) {
    require(STATE::class !in parents) { "State ${STATE::class.simpleName} already defined" }
    parents[STATE::class] = parent

    StateTransitionBuilder(STATE::class, this).apply(block).also { builder ->
      @Suppress("UNCHECKED_CAST")
      transitions[STATE::class] =
        builder.transitions as Map<KClass<out Event>, Transition<State, Event>>
    }
  }

  class StateTransitionBuilder<FromState : State, State : Any, Event : Any>(
    val from: KClass<FromState>,
    @PublishedApi internal val machineBuilder: StateMachineBuilder<State, Event>,
  ) {

    val transitions = mutableMapOf<KClass<out Event>, Transition<State, Event>>()

    /**
     * Declares a child of this state. Transitions not handled by the active child are resolved by
     * walking outward through its explicitly declared parents.
     */
    inline fun <reified CHILD : FromState> state(
      block: StateTransitionBuilder<CHILD, State, Event>.() -> Unit
    ) = machineBuilder.registerState(parent = from, block)

    inline fun <reified EVENT : Event> on(): TransitionBuilder<EVENT> =
      TransitionBuilder(EVENT::class)

    inner class TransitionBuilder<EVENT : Event>(val event: KClass<EVENT>) {

      inline infix fun <reified ToState : State> transitionWith(
        noinline transform: (FromState, EVENT) -> ToState
      ) {
        transitions[event] =
          Transition(
            id = TransitionId(from, event),
            from = from,
            to = ToState::class,
            event = event,
            reduce = { state, evt -> transform(state as FromState, evt as EVENT) },
          )
      }

      infix fun transitionTo(target: State) {
        transitions[event] =
          Transition(
            id = TransitionId(from, event),
            from = from,
            to = target::class,
            event = event,
            reduce = { _, _ -> target },
          )
      }
    }
  }

  fun build(): StateMachine<State, Event> {
    val initial = requireNotNull(initialState) { "initialState must be set" }
    val scope = requireNotNull(dispatchedOn) { "dispatchedOn must be set" }

    return StateMachine(
      initial = initial,
      transitions = transitions,
      parents = parents,
      scope = scope,
    )
  }
}
