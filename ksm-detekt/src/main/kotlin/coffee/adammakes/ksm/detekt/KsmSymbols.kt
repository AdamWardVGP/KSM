package coffee.adammakes.ksm.detekt

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtThisExpression

internal const val STATE_MACHINE = "coffee.adammakes.ksm.StateMachine"

internal fun KtSimpleNameExpression.resolvesTo(callableId: String): Boolean =
  analyze(this) {
    (mainReference.resolveToSymbol() as? KaCallableSymbol)
      ?.callableId
      ?.asSingleFqName()
      ?.asString() == callableId
  }

internal fun KtExpression.unwrapped(): KtExpression =
  if (this is KtParenthesizedExpression) expression?.unwrapped() ?: this else this

internal fun KtExpression.currentStateReceiver(): KtExpression? {
  val qualified = unwrapped() as? KtDotQualifiedExpression ?: return null
  val selector = qualified.selectorExpression as? KtSimpleNameExpression ?: return null
  return qualified.receiverExpression.takeIf { selector.resolvesTo("$STATE_MACHINE.currentState") }
}

// Compare stable receiver paths by declarations, not text (which conflates shadowed variables).
internal fun sameReceiver(first: KtExpression, second: KtExpression): Boolean {
  val left = first.unwrapped()
  val right = second.unwrapped()
  return when {
    left is KtThisExpression && right is KtThisExpression ->
      sameReceiver(left.instanceReference, right.instanceReference)
    left is KtSimpleNameExpression && right is KtSimpleNameExpression ->
      analyze(left) {
        val symbol = left.mainReference.resolveToSymbol()
        symbol != null && symbol == right.mainReference.resolveToSymbol()
      }
    left is KtDotQualifiedExpression && right is KtDotQualifiedExpression -> {
      val leftSelector = left.selectorExpression
      val rightSelector = right.selectorExpression
      leftSelector != null &&
        rightSelector != null &&
        sameReceiver(left.receiverExpression, right.receiverExpression) &&
        sameReceiver(leftSelector, rightSelector)
    }
    else -> false
  }
}
