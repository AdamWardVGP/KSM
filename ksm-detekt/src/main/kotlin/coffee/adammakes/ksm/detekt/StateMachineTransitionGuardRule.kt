package coffee.adammakes.ksm.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * Rejects `if` and `when` in direct KSM transition actions.
 *
 * This is the problem: the same declared source-state/event pair chooses between multiple target
 * state types inside a reducer, hiding those edges from the state graph. This is not limited to
 * guards that return the original state.
 *
 * ```kotlin
 * state<Connection> {
 *   on<RetryRequested>() transitionWith { state, _ ->
 *     when (state) {
 *       is OfflineWithCredentials -> Connecting
 *       is OfflineWithoutCredentials -> AwaitingCredentials
 *       else -> state
 *     }
 *   }
 * }
 * ```
 *
 * This is the preferred way: split the source states and declare a separate transition for each.
 * Each source-state/event pair has one target state type. KSM ignores events with no applicable
 * edge.
 *
 * ```kotlin
 * state<OfflineWithCredentials> {
 *   on<RetryRequested>() transitionTo Connecting
 * }
 * state<OfflineWithoutCredentials> {
 *   on<RetryRequested>() transitionTo AwaitingCredentials
 * }
 * ```
 *
 * Both conditional expressions and statements are checked. Separate nested lambdas, functions, and
 * objects are excluded, as are unrelated APIs named `transitionWith`. This opt-in architecture
 * policy also rejects pure calculations using direct conditionals; suppress it deliberately when
 * those calculations do not represent transition eligibility.
 */
class StateMachineTransitionGuardRule(config: Config) :
  Rule(config, "Conditional guards inside transition actions hide state machine decisions."),
  RequiresAnalysisApi {

  override val ruleName = RuleName("StateMachineTransitionGuard")

  override fun visitIfExpression(expression: KtIfExpression) {
    super.visitIfExpression(expression)
    checkConditional(expression, "if")
  }

  override fun visitWhenExpression(expression: KtWhenExpression) {
    super.visitWhenExpression(expression)
    checkConditional(expression, "when")
  }

  private fun checkConditional(expression: KtExpression, keyword: String) {
    var owner = expression.parent
    while (owner != null) {
      when (owner) {
        is KtNamedFunction,
        is KtClassOrObject -> return
        is KtLambdaExpression -> {
          if (owner.isTransitionAction()) {
            report(
              Finding(
                Entity.from(expression),
                "Remove the $keyword guard from the KSM transition action; model eligibility " +
                  "with states and events instead.",
              )
            )
          }
          return
        }
      }
      owner = owner.parent
    }
  }

  private fun KtLambdaExpression.isTransitionAction(): Boolean {
    var value: KtExpression = this
    while (value.parent is KtParenthesizedExpression) {
      value = value.parent as KtParenthesizedExpression
    }
    val reference =
      when (val argument = value.parent) {
        is KtLambdaArgument -> (argument.parent as? KtCallExpression)?.calleeExpression
        is KtValueArgument -> (argument.parent.parent as? KtCallExpression)?.calleeExpression
        is KtBinaryExpression -> argument.takeIf { it.right === value }?.operationReference
        else -> null
      }
        as? KtSimpleNameExpression ?: return false
    return reference.resolvesTo(
      "coffee.adammakes.ksm.StateMachineBuilder.StateTransitionBuilder.TransitionBuilder.transitionWith"
    )
  }
}
