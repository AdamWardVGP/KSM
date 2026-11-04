package coffee.adammakes.ksm.detekt

import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import dev.detekt.test.TestConfig
import java.util.ServiceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class KsmRuleSetProviderTest {
  @Test
  fun `service loader discovers KSM provider`() {
    val provider =
      ServiceLoader.load(RuleSetProvider::class.java)
        .filterIsInstance<KsmRuleSetProvider>()
        .single()
    assertEquals(RuleSetId("KSM"), provider.ruleSetId)
    assertEquals(RuleSetId("KSM"), provider.instance().id)
    assertEquals(
      setOf(
        "StateMachineTransitionGuard",
        "StateMachineEventDispatchGuard",
        "StateMachineStateObserver",
      ),
      provider.instance().rules.keys.map { it.value }.toSet(),
    )
  }

  @Test
  fun `factories preserve each rule configuration`() {
    KsmRuleSetProvider().instance().rules.forEach { (name, factory) ->
      val config = TestConfig("active" to true)
      val rule = factory(config)
      assertEquals(name, rule.ruleName)
      assertSame(config, rule.config)
    }
  }
}
