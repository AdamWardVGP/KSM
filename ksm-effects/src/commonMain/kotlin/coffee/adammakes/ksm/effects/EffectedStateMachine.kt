package coffee.adammakes.ksm.effects

import coffee.adammakes.ksm.StateMachine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EffectedStateMachine<State : Any, Event : Any>(
  private val machine: StateMachine<State, Event>,
  private val contributor: EffectContributor<State, Event>,
  parentScope: CoroutineScope,
) {

  val currentState: StateFlow<State> = machine.currentState

  init {
    parentScope.launch {
      var stateJob: Job? = null
      machine.currentState.collect { state ->
        stateJob?.cancel()
        val effects = contributor.effects(state)
        stateJob = launch {
          effects.forEach { effect -> launchEffect(this, state, effect) }
        }
      }
    }
  }

  fun dispatchEvent(event: Event) = machine.dispatchEvent(event)

  private fun launchEffect(scope: CoroutineScope, state: State, effect: Effect<State, Event>) {
    scope.launch {
      try {
        val result = effect.body(state)
        machine.dispatchEvent(result)
      } catch (e: CancellationException) {
        effect.onCancel?.let { onCancel ->
          withContext(NonCancellable) { machine.dispatchEvent(onCancel()) }
        }
        throw e
      }
    }
  }
}

/** Attaches an effect layer to this [StateMachine], bound to [scope]'s lifetime. */
fun <State : Any, Event : Any> StateMachine<State, Event>.withEffects(
  scope: CoroutineScope,
  contributor: EffectContributor<State, Event>,
): EffectedStateMachine<State, Event> = EffectedStateMachine(this, contributor, scope)
