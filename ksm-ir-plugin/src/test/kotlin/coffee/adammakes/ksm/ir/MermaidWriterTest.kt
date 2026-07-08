package coffee.adammakes.ksm.ir

import coffee.adammakes.ksm.ir.model.Edge
import coffee.adammakes.ksm.ir.model.Graph
import coffee.adammakes.ksm.ir.model.StateEffect
import org.junit.Test
import kotlin.test.assertEquals

class MermaidWriterTest {
    @Test
    fun `toMermaid produces correct output`() {
        val graph = Graph("TestGraph")
        graph.edges.add(Edge("Initial", "Move", "Final"))
        graph.edges.add(Edge("Final", "Reset", "Initial"))

        val expected = """
            ---
            config:
              layout: elk
            ---
            stateDiagram-v2
                Initial --> Final: Move
                Final --> Initial: Reset
        """.trimIndent()

        // MermaidWriter adds a newline at the end and uses 4 spaces for indentation
        val actual = MermaidWriter.toMermaid(graph).trim()
        assertEquals(expected, actual)
    }

    @Test
    fun `toMermaid marks effects as functions using fullwidth parens`() {
        val graph = Graph("TestGraph")
        graph.effects.getOrPut("Final") { mutableListOf() }.add(StateEffect("rainCoins", false))
        graph.effects.getOrPut("Final") { mutableListOf() }.add(StateEffect("stopMusic", true))

        val expected = """
            ---
            config:
              layout: elk
            ---
            stateDiagram-v2
                note right of Final
                    rainCoins﹙﹚
                    stopMusic﹙﹚ ↩
                end note
        """.trimIndent()

        val actual = MermaidWriter.toMermaid(graph).trim()
        assertEquals(expected, actual)
    }
}
