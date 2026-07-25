# Composite states

Composite states embed a fully independent child `StateMachine` — its own `State`/`Event`
types, unrelated to the parent's — inside a state of a parent `StateMachine`. Use them when a
self-contained flow needs to be reused across multiple parents (or multiple places within one
parent), or when a sub-flow is complex enough to deserve its own state graph rather than being
folded into the parent's.

This is different from [hierarchical states](../README.md#hierarchical-states), which nest
same-typed states so an unhandled event bubbles from a child up to its declared parent. A
composite child has its own type universe entirely — it can be defined once and embedded
anywhere a `StateMachine<ChildState, ChildEvent>` factory is accepted.

The sample app's combat mini-game (`sample/.../CombatStateMachine.kt`), embedded inside
`AdventureState.FightMonster` (`sample/.../AdventureStateMachine.kt`), is a complete worked
example — reachable by launching the sample and picking a fight.

## Declaring a composite child

`child` is called inside a `state<STATE> { }` block, alongside that state's own transitions:

```kotlin
state<AdventureState.FightMonster> {
    // The composite's own transitions are tried first — see "Interrupting a child" below.
    on<AdventureEvent.RunAway>() transitionWith { _, _ -> AdventureState.GameOver(...) }
    on<AdventureEvent.MonsterDefeated>() transitionTo AdventureState.Treasure
    on<AdventureEvent.PlayerDefeated>() transitionWith { _, _ -> AdventureState.GameOver(...) }

    combat = child(factory = { getCombatStateMachine(coroutineScope) }) {
        exit<CombatState.Won> { AdventureEvent.MonsterDefeated }
        exit<CombatState.Lost> { AdventureEvent.PlayerDefeated }
    }
}
```

`factory` builds a fresh child `StateMachine` — a new instance is created **every time** the
composite state is entered, so the same child definition is safe to reuse from multiple call
sites without carrying state over between them.

`child` returns a `CompositeHandle<Event, ChildEvent, ChildState>` — capture it (typically into a
`var` declared outside the builder, as in the sample) so calling code can dispatch to it and
observe the child.

## The handle: dispatch and observation

A composite child has its own `Event`/`State` types, which can't flow through the parent's own
`dispatchEvent`/`currentState` without breaking type safety. `CompositeHandle` gives you two
statically-typed entry points instead:

```kotlin
handle.dispatch(someParentEvent)   // routes through the parent, same as machine.dispatchEvent
handle.dispatch(someChildEvent)    // routes straight to the active child; no-op if inactive

handle.activeChildState            // StateFlow<ChildState?> — the child's live state while
                                    // active, null otherwise. No manual flow-combining needed.
```

## Event routing and interrupting a child

Events dispatched via `handle.dispatch(parentEvent)` are resolved by the parent exactly like any
other event: leaf-first, so the composite state's **own** declared transitions are tried before
walking out to its ancestors. This means a composite state can declare a transition that fires
regardless of what the child is currently doing — an interrupt/cancel, bypassing the child
entirely. In the sample, `RunAway` does exactly this: it ends the fight no matter what the
combat's current HP state is.

Events dispatched via `handle.dispatch(childEvent)` go straight to the active child and never
reach the parent's transition table — the two event types are unrelated, so there's nothing for
a child event to "bubble" into.

## Exit wiring

The child FSM's own definition never references the parent — `exit<ChildState> { event }`,
declared at the **embedding site**, is what closes the loop: when the child enters `ChildState`,
the given parent `Event` is automatically dispatched into the parent's own queue. The parent still
needs its own ordinary transition declared for that event (as in the example above) — exit wiring
only triggers the dispatch, it doesn't skip the parent's normal resolution.

Because the mapping lives at the embedding site, the same child FSM can be wired to different
exit events at different embedding sites without touching the child's own definition.

## Diagrams

Each `stateMachine { }` definition — parent or child — gets its own `.mmd` file. The parent's
diagram shows the composite state as a plain, collapsed node (just its inbound edge — the
child's internals aren't inlined there), plus a separate region expanding the child's real states
and a small mirror box of the parent states exit wiring lands on. The child's own file stays
completely parent-agnostic, so it looks identical no matter how many places embed it. See
`sample/ksmGraphs/stateMachine_AdventureState.mmd` and `stateMachine_CombatState.mmd` for the
sample's generated output.
