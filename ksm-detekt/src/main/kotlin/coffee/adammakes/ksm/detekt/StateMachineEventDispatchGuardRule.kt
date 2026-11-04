package coffee.adammakes.ksm.detekt

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * Rejects dispatch guarded by a read of the same KSM machine's current state.
 *
 * This is the problem: the caller duplicates the machine's transition eligibility rules.
 *
 * ```kotlin
 * fun onRetryClicked() {
 *   if (machine.currentState.value is Offline) {
 *     machine.dispatchEvent(RetryRequested)
 *   }
 * }
 * ```
 *
 * This is the preferred way: send the event unconditionally and let the graph decide whether it
 * applies.
 *
 * ```kotlin
 * fun onRetryClicked() {
 *   machine.dispatchEvent(RetryRequested)
 * }
 * // In the machine:
 * state<Offline> {
 *   on<RetryRequested>() transitionTo Connecting
 * }
 * ```
 *
 * Checks enclosing `if` and `when` branch conditions, including immutable local state aliases,
 * without crossing callable boundaries. Mere co-occurrence of a state read and unconditional
 * dispatch is not a finding. Both APIs and receiver declarations must resolve.
 */
class StateMachineEventDispatchGuardRule(config: Config) :
  Rule(config, "State-dependent event dispatch duplicates state machine transition decisions."),
  RequiresAnalysisApi {

  override val ruleName = RuleName("StateMachineEventDispatchGuard")

  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)
    val reference = expression.calleeExpression as? KtSimpleNameExpression ?: return
    val receiver = (expression.parent as? KtDotQualifiedExpression)?.receiverExpression ?: return
    if (reference.resolvesTo("$STATE_MACHINE.dispatchEvent")) checkDispatch(expression, receiver)
  }

  override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
    super.visitCallableReferenceExpression(expression)
    val receiver = expression.receiverExpression ?: return
    if (expression.callableReference.resolvesTo("$STATE_MACHINE.dispatchEvent")) {
      checkDispatch(expression, receiver)
    }
  }

  private fun checkDispatch(dispatch: KtExpression, receiver: KtExpression) {
    var child: PsiElement = dispatch
    var owner = child.parent
    while (owner != null) {
      val conditions: List<KtElement> =
        when (owner) {
          is KtNamedFunction,
          is KtClassOrObject,
          is KtLambdaExpression -> return
          is KtIfExpression ->
            if (
              PsiTreeUtil.isAncestor(owner.then, dispatch, false) ||
                PsiTreeUtil.isAncestor(owner.`else`, dispatch, false)
            )
              listOfNotNull(owner.condition)
            else emptyList()
          is KtWhenExpression ->
            if (owner.entries.any { it === child }) {
              listOfNotNull(owner.subjectExpression, owner.subjectVariable?.initializer) +
                owner.entries.take(owner.entries.indexOf(child) + 1).flatMap {
                  it.conditions.toList()
                }
            } else emptyList()
          else -> emptyList()
        }
      if (conditions.any { it.readsStateOf(receiver, mutableSetOf()) }) {
        report(
          Finding(
            Entity.from(dispatch),
            "Move transition eligibility into the state machine; dispatch the event and let " +
              "the declared state/event edges determine whether it applies.",
          )
        )
        return
      }
      child = owner
      owner = owner.parent
    }
  }

  private fun KtElement.readsStateOf(
    receiver: KtExpression,
    visited: MutableSet<KtProperty>,
  ): Boolean {
    var found = false
    accept(
      object : KtTreeVisitorVoid() {
        override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) = Unit

        override fun visitNamedFunction(function: KtNamedFunction) = Unit

        override fun visitClassOrObject(classOrObject: KtClassOrObject) = Unit

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
          val selector = expression.selectorExpression as? KtSimpleNameExpression
          if (selector?.resolvesTo("kotlinx.coroutines.flow.StateFlow.value") == true) {
            val machine = expression.receiverExpression.currentStateReceiver()
            if (machine != null && sameReceiver(machine, receiver)) found = true
          }
          super.visitDotQualifiedExpression(expression)
        }

        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
          super.visitSimpleNameExpression(expression)
          val property =
            analyze(expression) { expression.mainReference.resolveToSymbol()?.psi as? KtProperty }
          if (property != null && property.isLocal && !property.isVar && visited.add(property)) {
            if (property.initializer?.readsStateOf(receiver, visited) == true) found = true
          }
        }
      }
    )
    return found
  }
}
