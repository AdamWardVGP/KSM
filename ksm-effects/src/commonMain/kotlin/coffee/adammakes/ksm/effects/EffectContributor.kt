package coffee.adammakes.ksm.effects

import kotlin.reflect.KClass

fun interface EffectContributor<State : Any, Event : Any> {
  fun effects(state: State): (suspend (State) -> Event)?

  /**
   * Returns the effect registered for [stateClass] while [state] is the active concrete state.
   *
   * Custom contributors retain exact-state behavior by default. Contributors built with
   * [effectContributor] override this to support effects on explicitly declared parent states.
   */
  fun effectFor(stateClass: KClass<out State>, state: State): (suspend (State) -> Event)? =
    if (stateClass == state::class) effects(state) else null
}

class EffectContributorBuilder<State : Any, Event : Any> {

  @PublishedApi internal val handlers = mutableMapOf<KClass<out State>, suspend (State) -> Event>()

  inline fun <reified S : State> onEnter() = OnEnterScope<S, State, Event>(S::class, handlers)

  @PublishedApi
  internal fun build(): EffectContributor<State, Event> =
    object : EffectContributor<State, Event> {
      override fun effects(state: State): (suspend (State) -> Event)? = handlers[state::class]

      override fun effectFor(
        stateClass: KClass<out State>,
        state: State,
      ): (suspend (State) -> Event)? = handlers[stateClass]
    }
}

class OnEnterScope<S : State, State : Any, Event : Any>(
  @PublishedApi internal val klass: KClass<S>,
  @PublishedApi internal val handlers: MutableMap<KClass<out State>, suspend (State) -> Event>,
) {
  @Suppress("UNCHECKED_CAST")
  infix fun effect(body: suspend (S) -> Event) {
    check(klass !in handlers) { "Effect already registered for ${klass.simpleName}" }
    handlers[klass] = body as suspend (State) -> Event
  }
}

inline fun <State : Any, Event : Any> effectContributor(
  block: EffectContributorBuilder<State, Event>.() -> Unit
): EffectContributor<State, Event> = EffectContributorBuilder<State, Event>().apply(block).build()
