package coffee.adammakes.ksm

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
  private val parents: Map<KClass<out State>, KClass<out State>?> = emptyMap(),
  private val composites: Map<KClass<out State>, CompositeSpec<State, Event>> = emptyMap(),
) {

  constructor(
    initial: State,
    transitions: Map<KClass<out State>, Map<KClass<out Event>, Transition<State, Event>>>,
    scope: CoroutineScope,
  ) : this(initial, transitions, scope, emptyMap(), emptyMap())

  private val _currentState = MutableStateFlow(initial)

  /** The current state, as a [StateFlow]. Collect this to observe state changes. */
  public val currentState: StateFlow<State> = _currentState

  // UNLIMITED because:
  // - events are lightweight
  // - backpressure here would block callers, so allow everyone to enqueue
  // - state is single-writer so ordering is preserved
  private val events = Channel<Event>(Channel.UNLIMITED)

  private var activeCompositeOwner: KClass<out State>? = null
  private var activeChildJob: Job? = null
  private var activeChildRuntime: ChildRuntime<Event>? = null

  init {
    scope.launch {
      enterCompositeIfNeeded(initial)
      for (event in events) {
        val state = _currentState.value
        val transition = resolveTransition(state, event) ?: continue
        val newState = transition.reduce(state, event)
        _currentState.value = newState
        if (newState::class != state::class) {
          exitCompositeIfActive()
          enterCompositeIfNeeded(newState)
        }
      }
    }
  }

  private fun enterCompositeIfNeeded(state: State) {
    val spec = composites[state::class] ?: return
    val runtime = spec.createChild()
    activeCompositeOwner = state::class
    activeChildRuntime = runtime
    activeChildJob =
      scope.launch {
        runtime.currentStateFlow.collect { childState ->
          runtime.setActiveState(childState)
          runtime.exitEventFor(childState)?.let { dispatchEvent(it) }
        }
      }
  }

  private fun exitCompositeIfActive() {
    activeChildJob?.cancel()
    activeChildJob = null
    activeChildRuntime?.clear()
    activeChildRuntime = null
    activeCompositeOwner = null
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

  @PublishedApi
  internal val composites = mutableMapOf<KClass<out State>, CompositeSpec<State, Event>>()

  @PublishedApi internal var dispatchRef: ((Event) -> Unit)? = null

  @PublishedApi
  internal fun registerComposite(owner: KClass<out State>, createChild: () -> ChildRuntime<Event>) {
    require(owner !in composites) { "State ${owner.simpleName} already has a composite child" }
    composites[owner] = CompositeSpec(owner, createChild)
  }

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

    /**
     * Embeds an independent child [StateMachine] (its own [ChildState]/[ChildEvent] types) inside
     * this state. A fresh child instance is created via [factory] every time this state is entered.
     * Returns a [CompositeHandle] for dispatching to the parent or the active child, and for
     * observing the child's live state.
     *
     * [block] declares exit wiring: a child state that, once entered, auto-dispatches a parent
     * event as a side effect (see [CompositeChildBuilder.exit]).
     */
    inline fun <reified ChildState : Any, reified ChildEvent : Any> child(
      noinline factory: () -> StateMachine<ChildState, ChildEvent>,
      block: CompositeChildBuilder<ChildState, Event>.() -> Unit = {},
    ): CompositeHandle<Event, ChildEvent, ChildState> {
      val exitWiring = CompositeChildBuilder<ChildState, Event>().apply(block).exitWiring
      val activeChildState = MutableStateFlow<ChildState?>(null)
      var activeChild: StateMachine<ChildState, ChildEvent>? = null

      machineBuilder.registerComposite(
        owner = from,
        createChild = {
          val childMachine = factory()
          activeChild = childMachine
          ChildRuntime(
            currentStateFlow = childMachine.currentState,
            setActiveState = { state ->
              @Suppress("UNCHECKED_CAST")
              activeChildState.value = state as ChildState
            },
            exitEventFor = { state -> exitWiring[state::class]?.invoke() },
            clear = {
              activeChild = null
              activeChildState.value = null
            },
          )
        },
      )

      return CompositeHandle(
        dispatchParent = { event -> machineBuilder.dispatchRef?.invoke(event) },
        activeChildState = activeChildState,
        dispatchChild = { event -> activeChild?.dispatchEvent(event) },
      )
    }

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

    val machine =
      StateMachine(
        initial = initial,
        transitions = transitions,
        parents = parents,
        composites = composites,
        scope = scope,
      )
    dispatchRef = machine::dispatchEvent
    return machine
  }
}

/**
 * Owner-keyed record of a composite state's child factory. Internal wiring for [StateMachine],
 * built by [StateMachineBuilder.StateTransitionBuilder.child] — not intended to be constructed
 * directly.
 */
class CompositeSpec<State : Any, Event : Any>
@PublishedApi
internal constructor(val owner: KClass<out State>, val createChild: () -> ChildRuntime<Event>)

/**
 * Type-erased hooks a freshly-created composite child exposes to the owning [StateMachine]. Not
 * intended to be constructed directly.
 */
class ChildRuntime<Event : Any>
@PublishedApi
internal constructor(
  val currentStateFlow: StateFlow<Any>,
  val setActiveState: (Any) -> Unit,
  val exitEventFor: (Any) -> Event?,
  val clear: () -> Unit,
)

/**
 * Handle for a composite state's embedded child, returned by
 * [StateMachineBuilder.StateTransitionBuilder.child].
 *
 * Use [dispatch] with a parent [Event] to route through the parent's own transitions (consistent
 * leaf-first resolution — the composite's own declared transitions are tried before its ancestors,
 * so a parent event can interrupt/cancel regardless of the child's active state). Use [dispatch]
 * with a [ChildEvent] to route directly to the currently active child instance; silently dropped if
 * this composite isn't currently active, consistent with how unhandled events are dropped elsewhere
 * in KSM.
 *
 * [activeChildState] reflects the child's live state while this composite is active, and `null`
 * otherwise.
 */
class CompositeHandle<Event : Any, ChildEvent : Any, ChildState : Any>
@PublishedApi
internal constructor(
  private val dispatchParent: (Event) -> Unit,
  val activeChildState: StateFlow<ChildState?>,
  private val dispatchChild: (ChildEvent) -> Unit,
) {

  fun dispatch(event: Event) = dispatchParent(event)

  @JvmName("dispatchChildEvent") fun dispatch(event: ChildEvent) = dispatchChild(event)
}

/**
 * Builder for a composite state's exit wiring. Use via
 * [StateMachineBuilder.StateTransitionBuilder.child].
 */
class CompositeChildBuilder<ChildState : Any, Event : Any> @PublishedApi internal constructor() {

  @PublishedApi internal val exitWiring = mutableMapOf<KClass<*>, () -> Event>()

  /**
   * Declares that once the child enters [EXIT], the mapped [event] is automatically dispatched into
   * the parent's own event queue as a side effect. Works for any child state, not only a terminal
   * one.
   */
  inline fun <reified EXIT : ChildState> exit(noinline event: () -> Event) {
    exitWiring[EXIT::class] = event
  }
}
