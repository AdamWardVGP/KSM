package coffee.adammakes.ksm.effects

import kotlin.reflect.KClass

fun interface EffectContributor<State : Any, Event : Any> {
  fun effects(state: State): List<suspend (State) -> Event>
}

class EffectContributorBuilder<State : Any, Event : Any> {

  @PublishedApi
  internal val handlers = mutableMapOf<KClass<out State>, MutableList<suspend (State) -> Event>>()

  inline fun <reified S : State> onEnter() = OnEnterScope<S, State, Event>(S::class, handlers)

  @PublishedApi
  internal fun build(): EffectContributor<State, Event> = EffectContributor { state ->
    handlers[state::class] ?: emptyList()
  }
}

class OnEnterScope<S : State, State : Any, Event : Any>(
  @PublishedApi internal val klass: KClass<S>,
  @PublishedApi
  internal val handlers: MutableMap<KClass<out State>, MutableList<suspend (State) -> Event>>,
) {
  @Suppress("UNCHECKED_CAST")
  infix fun effect(body: suspend (S) -> Event): EffectAccumulator<S, State, Event> {
    val list = mutableListOf(body as suspend (State) -> Event)
    handlers[klass] = list
    return EffectAccumulator(klass, list)
  }
}

class EffectAccumulator<S : State, State : Any, Event : Any>(
  private val klass: KClass<S>,
  private val list: MutableList<suspend (State) -> Event>,
) {
  @Suppress("UNCHECKED_CAST")
  infix fun and(body: suspend (S) -> Event): EffectAccumulator<S, State, Event> {
    list.add(body as suspend (State) -> Event)
    return this
  }
}

inline fun <State : Any, Event : Any> effectContributor(
  block: EffectContributorBuilder<State, Event>.() -> Unit
): EffectContributor<State, Event> = EffectContributorBuilder<State, Event>().apply(block).build()
