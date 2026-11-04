package coffee.adammakes.ksm.detekt

import coffee.adammakes.ksm.detekt.RuleTestFixtures.lintKsm
import dev.detekt.api.Config
import org.junit.Assert.assertEquals
import org.junit.Test

class StateMachineStateObserverRuleTest {
  private val subject = StateMachineStateObserverRule(Config.empty)

  @Test
  fun `reports resolved direct onEach including import aliases and parentheses`() {
    assertEquals(
      3,
      subject
        .lintKsm(
          """
        import coffee.adammakes.ksm.StateMachine
        import kotlinx.coroutines.flow.onEach
        import kotlinx.coroutines.flow.onEach as observe
        import kotlinx.coroutines.flow.launchIn
        fun example(machine: StateMachine<Int, Int>, scope: Any) {
            machine.currentState.onEach { println(it) }.launchIn(scope)
            (machine.currentState).onEach { println(it) }
            machine.currentState.observe { println(it) }
        }
        """
        )
        .size,
    )
  }

  @Test
  fun `ignores transformations unrelated flows and unrelated currentState properties`() {
    assertEquals(
      0,
      subject
        .lintKsm(
          """
        import coffee.adammakes.ksm.StateMachine
        import kotlinx.coroutines.flow.*
        class Other(val currentState: StateFlow<Int>)
        fun example(machine: StateMachine<Int, Int>, other: Other, preferences: Flow<Int>) {
            machine.currentState.map { it + 1 }
            machine.currentState.map { it + 1 }.onEach { println(it) }
            preferences.onEach { println(it) }
            other.currentState.onEach { println(it) }
        }
        """
        )
        .size,
    )
  }

  @Test
  fun `ignores unrelated onEach extension on actual KSM state`() {
    assertEquals(
      0,
      subject
        .lintKsm(
          """
        import coffee.adammakes.ksm.StateMachine
        import kotlinx.coroutines.flow.StateFlow
        fun StateFlow<Int>.onEach(action: (Int) -> Unit) {}
        fun example(machine: StateMachine<Int, Int>) {
            machine.currentState.onEach { println(it) }
        }
        """
        )
        .size,
    )
  }
}
