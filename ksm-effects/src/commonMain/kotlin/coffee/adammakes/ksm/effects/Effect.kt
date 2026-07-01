package coffee.adammakes.ksm.effects

/**
 * A side effect bound to a state's lifetime. [body] suspends to do work and returns the [Event]
 * reporting the outcome - factual (e.g. CommandFailed)
 *
 * [onCancel] is the cleanup path. It runs inside withContext(NonCancellable) when the state
 * scope is cancelled, so real cleanup can suspend safely.
 *
 * The returned event is pushed directly into the machine's event sink.
 *
 * State is a parameter source. No branching on state type inside [body]; to avoid a
 * second decision point alongside the reducer.
 */
data class Effect<State : Any, Event : Any>(
  val body: suspend (State) -> Event,
  val onCancel: (suspend () -> Event)? = null,
)
