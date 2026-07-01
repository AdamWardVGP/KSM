package coffee.adammakes.ksm.effects

/** Computes the active [Effect]s for a given [State]. Called on every state entry. */
fun interface EffectContributor<State : Any, Event : Any> {
  fun effects(state: State): List<Effect<State, Event>>
}
