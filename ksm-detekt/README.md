# KSM Detekt rules

Opt-in architecture checks for KSM applications. This **JVM-only, build-time plugin**
does not depend on the KSM runtime or bundle Detekt. Nothing changes in an application's
runtime behavior when these rules are enabled.

## Installation and compatibility

**Unreleased:** the artifact is `coffee.adammakes.ksm:ksm-detekt:<version>`, with its
version inherited from the root `VERSION_NAME`. The existing KSM `2.0.0` release predates
this module. Wait for an authorized release containing it and verify availability on Maven
Central before replacing local rules.

The plugin targets **Detekt 2.0.0-alpha.6** (`dev.detekt` API) and **JDK 17+**.
It is not compatible with Detekt 1.x; the repository's own Detekt 1.23.8 lint setup remains
unchanged. Other Detekt 2 alpha versions are not guaranteed binary-compatible, particularly
for Kotlin Analysis API rules.

After the artifact is published, add it to the consumer's Detekt plugin classpath, **not**
`implementation`:

```kotlin
plugins {
    id("dev.detekt") version "2.0.0-alpha.6"
}

dependencies {
    detektPlugins("coffee.adammakes.ksm:ksm-detekt:<published-version>")
}
```

All three rules require type resolution. Run a type-resolving Detekt task (for example,
`detektMain` on a Kotlin/JVM consumer), with KSM and the consumer's dependencies on the
analysis classpath. Plain syntax-only `detekt` tasks do not run these rules. For a CLI
invocation supply the compiled dependency classpath with `--classpath`.

Enable only the policies your project wants:

```yaml
KSM:
  active: true
  StateMachineTransitionGuard:
    active: true
  StateMachineEventDispatchGuard:
    active: true
  StateMachineStateObserver:
    active: true
```

The rule set and each rule default to inactive. Ordinary Detekt `active`, `excludes`,
baselines, and `@Suppress` configuration apply independently. For example, intentional
read-only observers may use `@Suppress("KSM:StateMachineStateObserver")`.
These are architecture preferences, not claims that every reported program is incorrect.

## StateMachineTransitionGuard

### This is the problem

A transition action should construct the destination state for its declared edge.
An `if` or `when` inside the action can instead decide whether the event applies or
which edge to take, hiding that decision from the state graph. In this example,
the same declared `Connection`/`RetryRequested` pair chooses between different target
state types:

```kotlin
state<Connection> {
    on<RetryRequested>() transitionWith { state, _ ->
        when (state) {
            is OfflineWithCredentials -> Connecting
            is OfflineWithoutCredentials -> AwaitingCredentials
            else -> state
        }
    }
}
```

Both target-selecting branches are part of the problem, not just `else -> state`.
The rule reports the `when` regardless of its number of branches, even if every branch
returns a new state. Equivalent `if`/`else` target selection is also reported.

### This is the preferred way

Split the source states and declare a separate transition for each: **one source-state/event
pair has one target state type**. If the distinction currently exists only as a condition
on state data, represent it as distinct source states instead. KSM ignores an event when
there is no applicable edge:

```kotlin
state<OfflineWithCredentials> {
    on<RetryRequested>() transitionTo Connecting
}
state<OfflineWithoutCredentials> {
    on<RetryRequested>() transitionTo AwaitingCredentials
}
```

For payload-driven transitions, keep the action straight-line:

```kotlin
state<Connecting> {
    on<ConnectionFailed>() transitionWith { _, event -> Offline(event.reason) }
}
```

**Scope:** every direct `if` and `when`, including subjectful/subjectless `when`,
statements, expressions, and early-return guards. Dotted calls, infix calls, named
arguments, and parenthesized lambda arguments are supported. Separate nested lambdas,
local/anonymous functions, and classes/objects are excluded. Unrelated `transitionWith`
APIs are ignored through resolved callable ownership.

The rule deliberately does not try to distinguish eligibility from pure calculations:
even a direct conditional used only for formatting is reported. Suppress an intentional
exception rather than moving eligibility into a nested callable merely to evade the rule.
Actions passed through variables or function references are outside its scope.

## StateMachineEventDispatchGuard

### This is the problem

The caller duplicates a transition decision that belongs in the machine:

```kotlin
fun onRetryClicked() {
    if (machine.currentState.value is Offline) {
        machine.dispatchEvent(RetryRequested)
    }
}
```

A `when` selecting dispatch based on the same machine's current state has the same issue.

### This is the preferred way

Send the event without checking the current state and declare its applicable edge:

```kotlin
fun onRetryClicked() {
    machine.dispatchEvent(RetryRequested)
}

// In the machine:
state<Offline> {
    on<RetryRequested>() transitionTo Connecting
}
```

**Scope:** a resolved KSM `dispatchEvent` call or callable reference inside an `if`/`when`
branch guarded by the same resolved receiver's `currentState.value`. Immutable local
state/boolean aliases are followed. Receiver declarations, not just receiver text, are
compared, so shadowed names and different machines are not conflated. Guard checks stop
at callable/class boundaries; guards inside callbacks and initializers are checked in
their own scope.

Reading state for display or logging and then dispatching unconditionally is allowed.
This is intentionally narrower than a whole-function "state read plus dispatch" check.
The rule is not a general data-flow analysis: it does not follow mutable aliases,
machine aliases, helper calls, early-return guards preceding a dispatch, event payload
construction, or state-derived nullable chains such as
`toEvent(machine.currentState.value)?.let(machine::dispatchEvent)`.
Receiver expressions that invoke functions are not equated by spelling.

## StateMachineStateObserver

### This is the problem

State-entry work in an external collector is not represented by KSM's effect declarations
or Mermaid diagram. The collector also has to manage cancellation and dispatch feedback:

```kotlin
machine.currentState
    .onEach { state -> if (state is Loading) load(state) }
    .launchIn(scope)
```

### This is the preferred way

Use `ksm-effects` to register work for an entered state. The effect receives that state,
returns a result event, and is cancelled when its state exits:

```kotlin
machine.withEffects(scope) {
    onEnter<Loading>() effect ::load
}
```

**Scope:** direct `currentState.onEach` where `currentState` resolves to KSM and `onEach`
resolves to `kotlinx.coroutines.flow.onEach`, including import aliases and parentheses.
Unrelated APIs with either name are ignored. Transformations such as `currentState.map`
and transformed-flow observers are not reported.

The rule does **not** infer whether the observer actually performs side effects. It is
an optional policy against direct observers, not a ban on observing state for UI,
logging, or diagnostics. Leave this rule disabled or suppress intentional read-only
observers. It does not inspect `collect`, aliases, or arbitrary Flow chains.

## Building and publication

```shell
./gradlew :ksm-detekt:check :ksm-detekt:checkPomFileForMavenPublication
```

The module uses the same Maven Central publication and optional signing conventions as
KSM's other modules. It publishes one JVM jar plus sources and documentation artifacts;
there are no application runtime dependencies in its POM. Release CI includes the module,
but building or merging this change does not publish it. See [RELEASING.md](../RELEASING.md)
for the separately authorized release procedure.
