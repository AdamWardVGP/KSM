package org.example

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.also
import kotlin.reflect.KClass


data class TransitionId(
    val from: KClass<*>,
    val event: KClass<*>
)

data class Transition<State : Any, Event : Any>(
    val id: TransitionId,
    val from: KClass<out State>,
    val event: KClass<out Event>,
    val reduce: (State, Event) -> State
)

/**
 * A finite state machine intended for use in view models. Think of this like a flow-chart for sequences of states,
 * guided by events.
 *
 * A single [State] is persisted and awaits an [Event]. If a relevant [Event] is
 * received, we enter a new [State], and listeners are notified that a
 * [Transition] including Previous State, Event that caused the transition, and New State
 *
 * States must be unique in the graph, may be terminal with no transitions, and by design, unhandled events are ignored.
 */
class StateMachine<State: Any, Event: Any>(
    initial: State,
    val transitions: Map<KClass<out State>, Map<KClass<out Event>, Transition<State, Event>>>,
    scope: CoroutineScope
) {

    private val _currentState = MutableStateFlow(initial)
    val currentState: StateFlow<State> = _currentState

    // UNLIMITED because:
    // - events are lightweight
    // - backpressure here would block callers, so allow everyone to enqueue
    // - state is single-writer so ordering is preserved
    private val events = Channel<Event>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (event in events) {
                val stateClass = _currentState.value::class
                val eventClass = event::class

                val transition =
                    transitions[stateClass]?.get(eventClass) ?: continue

                val newState = transition.reduce(_currentState.value, event)
                _currentState.value = newState
            }
        }
    }

    fun dispatchEvent(event: Event) {
        events.trySend(event)
    }
}

//region Builder Utilities

/**
 * We create a DSL to have nicer state machine definition syntax. The goal is something
 * like this
 * val fsm = stateMachine<AppState, AppEvent> {
 *
 *     initialState = Init
 *     dispatchedOn = viewModelScope
 *
 *     state(Init) {
 *         on(RequestEula) transitionTo EulaDialog
 *     }
 *
 *     state(EulaDialog) {
 *         on(EulaPassed) transitionTo PermissionRequest
 *         on(EulaDenied) transitionTo ExitApp
 *     }
 * }
 */
inline fun <reified State: Any, reified Event: Any> stateMachine(
    block: StateMachineBuilder<State, Event>.() -> Unit
): StateMachine<State,Event> =
    StateMachineBuilder<State, Event>().apply(block).build()

class StateMachineBuilder<State: Any, Event: Any> {

    var initialState: State? = null
    var dispatchedOn: CoroutineScope? = null


    val transitions = mutableMapOf<KClass<out State>, Map<KClass<out Event>, Transition<State, Event>>>()


    inline fun <reified STATE: State> state(block: StateTransitionBuilder<STATE, Event>.() -> Unit) {
        StateTransitionBuilder<STATE, Event>(STATE::class).apply(block).also { builder ->

            require(STATE::class !in transitions) {
                "State ${STATE::class.simpleName} already defined"
            }

            transitions[STATE::class] = builder.transitions as Map<KClass<out Event>, Transition<State, Event>>

        }
    }

     class StateTransitionBuilder<State : Any, Event : Any>(private val from: KClass<State>) {

        val transitions = mutableMapOf<KClass<out Event>, Transition<State, Event>>()

         inline fun <reified EVENT : Event> on(): TransitionBuilder<EVENT> =
             TransitionBuilder(EVENT::class)

        inner class TransitionBuilder<EVENT: Event>(private val event: KClass<EVENT>) {

            infix fun transitionTo(target: (State, EVENT) -> State) {
                transitions[event] = Transition(
                    id = TransitionId(from, event),
                    from = from,
                    event = event,
                    reduce = { state, evt -> target(state, evt as EVENT) }
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
            scope = scope
        )
    }
}



