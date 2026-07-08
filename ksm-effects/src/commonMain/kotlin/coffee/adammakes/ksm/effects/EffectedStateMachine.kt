package coffee.adammakes.ksm.effects

import coffee.adammakes.ksm.StateMachine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
        stateJob = launch {
          contributor.effects(state)?.let { body ->
            launch {
              val event =
                try {
                  body(state)
                } catch (e: CancellationException) {
                  throw e
                } catch (e: Throwable) {
                  return@launch
                }
              machine.dispatchEvent(event)
            }
          }
        }
      }
    }
  }

  fun dispatchEvent(event: Event) = machine.dispatchEvent(event)
}

fun <State : Any, Event : Any> StateMachine<State, Event>.withEffects(
  scope: CoroutineScope,
  contributor: EffectContributor<State, Event>,
): EffectedStateMachine<State, Event> = EffectedStateMachine(this, contributor, scope)

inline fun <State : Any, Event : Any> StateMachine<State, Event>.withEffects(
  scope: CoroutineScope,
  block: EffectContributorBuilder<State, Event>.() -> Unit,
): EffectedStateMachine<State, Event> = withEffects(scope, effectContributor(block))
