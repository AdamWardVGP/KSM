package coffee.adammakes.ksm.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class KsmRuleSetProvider : RuleSetProvider {
  override val ruleSetId = RuleSetId("KSM")

  override fun instance() =
    RuleSet(
      ruleSetId,
      listOf(
        ::StateMachineTransitionGuardRule,
        ::StateMachineEventDispatchGuardRule,
        ::StateMachineStateObserverRule,
      ),
    )
}
