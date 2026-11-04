package coffee.adammakes.ksm.detekt

import coffee.adammakes.ksm.detekt.RuleTestFixtures.lintKsm
import dev.detekt.api.Config
import org.junit.Assert.assertEquals
import org.junit.Test

class StateMachineEventDispatchGuardRuleTest {
  private val subject = StateMachineEventDispatchGuardRule(Config.empty)

  @Test
  fun `reports direct if when and subjectless when guards`() {
    listOf(
        "if (machine.currentState.value > 0) machine.dispatchEvent(1)",
        "when (machine.currentState.value) { 0 -> machine.dispatchEvent(1); else -> Unit }",
        "when (val state = machine.currentState.value) { 0 -> machine.dispatchEvent(state); else -> Unit }",
        "when { machine.currentState.value > 0 -> machine.dispatchEvent(1); else -> Unit }",
        "if ((machine).currentState.value > 0) (machine).dispatchEvent(1)",
        "if (machine.currentState.value > 0) Unit else machine.dispatchEvent(1)",
      )
      .forEach { assertEquals(it, 1, lintBody(it).size) }
  }

  @Test
  fun `follows immutable local state and boolean aliases`() {
    assertEquals(
      1,
      lintBody(
          """
        val state = machine.currentState.value
        val eligible = state > 0
        if (eligible) machine.dispatchEvent(1)
        """
        )
        .size,
    )
  }

  @Test
  fun `reports callable references under a state guard`() {
    assertEquals(
      1,
      lintBody("if (machine.currentState.value > 0) listOf(1).forEach(machine::dispatchEvent)").size,
    )
  }

  @Test
  fun `does not report unrelated reads followed by unconditional dispatch`() {
    assertEquals(
      0,
      lintBody(
          """
        println(machine.currentState.value)
        if (machine.currentState.value > 0) println("loading")
        machine.dispatchEvent(1)
        """
        )
        .size,
    )
  }

  @Test
  fun `does not infer eligibility from state-derived event payloads or nullable chains`() {
    assertEquals(
      0,
      lintBody(
          """
        fun toEvent(state: Int): Int? = state.takeIf { it > 0 }
        machine.dispatchEvent(machine.currentState.value)
        toEvent(machine.currentState.value)?.let(machine::dispatchEvent)
        """
        )
        .size,
    )
  }

  @Test
  fun `ignores guards using another machine and unrelated flags`() {
    assertEquals(
      0,
      lintBody(
          """
        println(machine.currentState.value)
        if (other.currentState.value > 0) machine.dispatchEvent(1)
        if (flag) machine.dispatchEvent(2)
        """
        )
        .size,
    )
  }

  @Test
  fun `does not cross callable or object boundaries or duplicate nested findings`() {
    assertEquals(
      1,
      lintBody(
          """
        if (machine.currentState.value > 0) {
            val later = { machine.dispatchEvent(1) }
            fun laterFunction() { machine.dispatchEvent(2) }
            val holder = object { init { machine.dispatchEvent(3) } }
            if (machine.currentState.value > 1) machine.dispatchEvent(4)
        }
        """
        )
        .size,
    )
  }

  @Test
  fun `reports guards inside callbacks and initializers`() {
    assertEquals(
      2,
      subject
        .lintKsm(
          """
        import coffee.adammakes.ksm.StateMachine
        class Example(val machine: StateMachine<Int, Int>) {
            init {
                if (machine.currentState.value > 0) machine.dispatchEvent(1)
                listOf(1).forEach {
                    if (machine.currentState.value > 0) machine.dispatchEvent(it)
                }
            }
        }
        """
        )
        .size,
    )
  }

  @Test
  fun `ignores mutable aliases and shadowed receiver names`() {
    assertEquals(
      0,
      lintBody(
          """
        var state = machine.currentState.value
        state = 42
        if (state > 0) machine.dispatchEvent(1)
        if (machine.currentState.value > 0) {
            val machine = other
            machine.dispatchEvent(1)
        }
        """
        )
        .size,
    )
  }

  @Test
  fun `later when conditions do not guard earlier branches`() {
    assertEquals(
      0,
      lintBody(
          """
        when {
            flag -> machine.dispatchEvent(1)
            machine.currentState.value > 0 -> println("loading")
        }
        """
        )
        .size,
    )
  }

  @Test
  fun `ignores unrelated APIs with the same names`() {
    assertEquals(
      0,
      subject
        .lintKsm(
          """
        class Snapshot(val value: Int)
        class Other(val currentState: Snapshot) { fun dispatchEvent(event: Int) {} }
        fun example(machine: Other) {
            if (machine.currentState.value > 0) machine.dispatchEvent(1)
        }
        """
        )
        .size,
    )
  }

  @Test
  fun `matches qualified receiver paths including explicit this`() {
    assertEquals(
      1,
      subject
        .lintKsm(
          """
        import coffee.adammakes.ksm.StateMachine
        class Example(val machine: StateMachine<Int, Int>) {
            fun example() {
                if (this.machine.currentState.value > 0) this.machine.dispatchEvent(1)
            }
        }
        """
        )
        .size,
    )
  }

  @Test
  fun `ignores state reads in deferred condition callbacks`() {
    assertEquals(
      0,
      lintBody(
          """
        val eligible = { machine.currentState.value > 0 }
        if (eligible()) machine.dispatchEvent(1)
        """
        )
        .size,
    )
  }

  private fun lintBody(body: String) =
    subject.lintKsm(
      """
    import coffee.adammakes.ksm.StateMachine
    fun example(machine: StateMachine<Int, Int>, other: StateMachine<Int, Int>, flag: Boolean) {
        $body
    }
    """
    )
}
