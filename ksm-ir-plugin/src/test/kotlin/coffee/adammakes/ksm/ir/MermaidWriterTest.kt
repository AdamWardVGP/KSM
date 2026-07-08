package coffee.adammakes.ksm.ir

import coffee.adammakes.ksm.ir.model.Edge
import coffee.adammakes.ksm.ir.model.Graph
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
}
