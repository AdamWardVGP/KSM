package coffee.adammakes.ksm.detekt

import coffee.adammakes.ksm.detekt.RuleTestFixtures.lintKsm
import dev.detekt.api.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateMachineTransitionGuardRuleTest {
  private val subject = StateMachineTransitionGuardRule(Config.empty)

  @Test
  fun `reports if and both when forms in every supported call syntax`() {
    val bodies =
      listOf(
        "if (event > 0) state else state + event",
        "when (event) { 0 -> state; else -> state + event }",
        "when { event > 0 -> state; else -> state + event }",
      )
    for (body in bodies) {
      val action = "{ state, event -> $body }"
      val calls =
        listOf(
          "on<Int>().transitionWith<Int> $action",
          "on<Int>().transitionWith<Int>($action)",
          "on<Int>().transitionWith<Int>(transform = (($action)))",
          "on<Int>() transitionWith $action",
          "on<Int>() transitionWith (($action))",
        )
      for (call in calls) {
        val findings = lintAction(call)
        assertEquals(call, 1, findings.size)
        val keyword = if (body.startsWith("if")) "if" else "when"
        assertTrue(findings.single().message.contains("Remove the $keyword guard"))
      }
    }
  }

  @Test
  fun `reports each conditional including mixed nested branches and statements`() {
    assertEquals(
      5,
      lintAction(
          """
        on<Int>() transitionWith { state, event ->
            when {
                event > 0 -> if (event > 1) println(event)
                else -> when (event) { 0 -> println(state); else -> println(event) }
            }
            if (event < 0) {
                when (event) { -1 -> println(state); else -> println(event) }
            }
            state
        }
        """
        )
        .size,
    )
  }

  @Test
  fun `reports early returns for if and when`() {
    listOf(
        "if (event == 0) return@transitionWith state",
        "when { event == 0 -> return@transitionWith state; else -> println(event) }",
      )
      .forEach { guard ->
        assertEquals(
          1,
          lintAction("on<Int>().transitionWith<Int> { state, event -> $guard; state + event }").size,
        )
      }
  }

  @Test
  fun `ignores separate nested lambdas functions anonymous functions and objects`() {
    assertEquals(
      0,
      lintAction(
          """
        on<Int>() transitionWith { state, event ->
            listOf(event).map { if (it > 0) it else 0 }
            listOf(event).map { when (it) { 0 -> 1; else -> 0 } }
            fun choose(): Int = if (event > 0) 1 else 0
            fun chooseWhen(): Int = when (event) { 0 -> 1; else -> 0 }
            val later = fun(): Int = if (event > 0) 1 else 0
            val laterWhen = fun(): Int = when (event) { 0 -> 1; else -> 0 }
            val holder = object {
                val edit = if (event > 0) 1 else 0
                val other = when (event) { 0 -> 1; else -> 0 }
            }
            state
        }
        """
        )
        .size,
    )
  }

  @Test
  fun `ignores unrelated transitionWith even inside a machine`() {
    assertEquals(
      0,
      lintAction(
          """
        class Other { fun transitionWith(action: () -> Int) = action() }
        Other().transitionWith { if (true) 1 else 0 }
        Other().transitionWith { when { true -> 1; else -> 0 } }
        """
        )
        .size,
    )
  }

  @Test
  fun `accepts straight line actions and conditions outside actions`() {
    assertEquals(
      0,
      lintAction(
          """
        if (true) on<Int>() transitionWith { state, event -> state + event }
        when { true -> on<Int>() transitionWith { state, event -> state + event } }
        """
        )
        .size,
    )
    assertEquals(
      0,
      subject
        .lintKsm(
          """
        fun transitionWith(action: () -> Int) = action()
        fun example() = transitionWith { if (true) 1 else 0 }
        fun exampleWhen() = transitionWith { when { true -> 1; else -> 0 } }
        """
        )
        .size,
    )
  }

  @Test
  fun `reports guarded actions independently`() {
    assertEquals(
      2,
      lintAction(
          """
        on<Int>() transitionWith { state, event -> if (event > 0) state else event }
        on<Int>() transitionWith { state, event -> when (event) { 0 -> state; else -> event } }
        """
        )
        .size,
    )
  }

  private fun lintAction(action: String) =
    subject.lintKsm(
      """
    import coffee.adammakes.ksm.StateMachineBuilder
    fun example() {
        StateMachineBuilder<Int, Int>().state<Int> { $action }
    }
    """
    )
}
