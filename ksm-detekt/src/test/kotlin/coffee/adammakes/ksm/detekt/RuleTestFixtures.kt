package coffee.adammakes.ksm.detekt

import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment

internal object RuleTestFixtures {
  private val environment = createEnvironment()

  fun <T> T.lintKsm(code: String) where T : Rule, T : RequiresAnalysisApi =
    lintWithContext(environment, code.trimIndent(), ksm, flows)

  // Source fixtures preserve KSM's member ownership without coupling the plugin to its runtime.
  private val ksm =
    """
    package coffee.adammakes.ksm
    import kotlinx.coroutines.flow.StateFlow

    class StateMachine<State : Any, Event : Any>(val currentState: StateFlow<State>) {
        fun dispatchEvent(event: Event) {}
    }
    class StateMachineBuilder<State : Any, Event : Any> {
        fun <From : State> state(block: StateTransitionBuilder<From, State, Event>.() -> Unit) {}
        class StateTransitionBuilder<From : State, State : Any, Event : Any> {
            fun <E : Event> on(): TransitionBuilder<E> = TransitionBuilder()
            inner class TransitionBuilder<E : Event> {
                infix fun <To : State> transitionWith(transform: (From, E) -> To) {}
            }
        }
    }
    """
      .trimIndent()

  private val flows =
    """
    package kotlinx.coroutines.flow
    interface Flow<out T>
    interface StateFlow<out T> : Flow<T> { val value: T }
    fun <T> Flow<T>.onEach(action: suspend (T) -> Unit): Flow<T> = this
    fun <T, R> Flow<T>.map(transform: suspend (T) -> R): Flow<R> = TODO()
    fun <T> Flow<T>.launchIn(scope: Any) {}
    """
      .trimIndent()
}
