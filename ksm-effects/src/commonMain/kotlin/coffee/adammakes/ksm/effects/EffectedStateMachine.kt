package coffee.adammakes.ksm.effects

import coffee.adammakes.ksm.StateMachine
import kotlin.reflect.KClass
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
      var previousPath = emptyList<KClass<out State>>()
      val effectJobs = mutableMapOf<KClass<out State>, Job>()

      machine.currentState.collect { state ->
        val currentPath = machine.statePath(state)
        val sharedPathSize =
          sharedPathSize(previousPath, currentPath).let { sharedSize ->
            if (previousPath == currentPath && currentPath.isNotEmpty()) {
              sharedSize - 1
            } else {
              sharedSize
            }
          }

        previousPath.drop(sharedPathSize).asReversed().forEach { stateClass ->
          effectJobs.remove(stateClass)?.cancel()
        }

        currentPath.drop(sharedPathSize).forEach { stateClass ->
          contributor.effectFor(stateClass, state)?.let { body ->
            effectJobs[stateClass] = launch {
              val event =
                try {
                  body(state)
                } catch (e: CancellationException) {
                  throw e
                } catch (
                  @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Throwable) {
                  // An effect body is arbitrary user code; isolate failures to this effect so
                  // other active hierarchy effects and future states continue to run.
                  return@launch
                }
              machine.dispatchEvent(event)
            }
          }
        }

        previousPath = currentPath
      }
    }
  }

  fun dispatchEvent(event: Event) = machine.dispatchEvent(event)

  private fun sharedPathSize(
    previous: List<KClass<out State>>,
    current: List<KClass<out State>>,
  ): Int {
    var sharedSize = 0
    while (
      sharedSize < previous.size &&
        sharedSize < current.size &&
        previous[sharedSize] == current[sharedSize]
    ) {
      sharedSize += 1
    }
    return sharedSize
  }
}

fun <State : Any, Event : Any> StateMachine<State, Event>.withEffects(
  scope: CoroutineScope,
  contributor: EffectContributor<State, Event>,
): EffectedStateMachine<State, Event> = EffectedStateMachine(this, contributor, scope)

inline fun <State : Any, Event : Any> StateMachine<State, Event>.withEffects(
  scope: CoroutineScope,
  block: EffectContributorBuilder<State, Event>.() -> Unit,
): EffectedStateMachine<State, Event> = withEffects(scope, effectContributor(block))
