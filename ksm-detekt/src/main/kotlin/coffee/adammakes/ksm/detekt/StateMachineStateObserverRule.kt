package coffee.adammakes.ksm.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

/**
 * Opt-in policy reserving direct KSM currentState.onEach observers for the effect layer.
 *
 * This is the problem: external collectors hide state-entry work from the machine's effect
 * declarations and Mermaid diagram, and must manage cancellation and feedback themselves.
 *
 * ```kotlin
 * machine.currentState
 *   .onEach { state -> if (state is Loading) load(state) }
 *   .launchIn(scope)
 * ```
 *
 * This is the preferred way: register state-entry work with KSM Effects, which owns cancellation
 * when the state exits and dispatches the returned result event.
 *
 * ```kotlin
 * machine.withEffects(scope) {
 *   onEnter<Loading>() effect ::load
 * }
 * ```
 *
 * Requires resolved KSM `currentState` and kotlinx.coroutines Flow `onEach`, not just matching
 * names. This rule does not infer side effects: suppress it for intentional read-only observers, or
 * leave it disabled when direct observation is part of the application's architecture.
 */
class StateMachineStateObserverRule(config: Config) :
  Rule(config, "Prefer withEffects for state-entry work instead of currentState.onEach."),
  RequiresAnalysisApi {

  override val ruleName = RuleName("StateMachineStateObserver")

  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)
    val reference = expression.calleeExpression as? KtSimpleNameExpression ?: return
    if (!reference.resolvesTo("kotlinx.coroutines.flow.onEach")) return
    val qualified = expression.parent as? KtDotQualifiedExpression ?: return
    if (qualified.receiverExpression.currentStateReceiver() == null) return
    report(
      Finding(
        Entity.from(expression),
        "Model state-entry side effects with withEffects { onEnter<State>() effect ... }; " +
          "suppress this opt-in policy for intentional read-only observers.",
      )
    )
  }
}
