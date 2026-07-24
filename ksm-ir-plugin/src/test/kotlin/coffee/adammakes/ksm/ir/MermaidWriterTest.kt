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

    @Test
    fun `toMermaid renders DSL hierarchy with scoped transitions and effects`() {
        val graph = Graph("TestGraph")
        graph.declareState("test.Parent", "Parent", null)
        graph.declareState("other.Child", "Child", "test.Parent")
        graph.declareState("test.Done", "Done", null)
        graph.edges.add(Edge("test.Parent", "ParentEvent", "test.Done"))
        graph.edges.add(Edge("other.Child", "ChildEvent", "test.Parent"))
        graph.effects.getOrPut("other.Child") { mutableListOf() }
            .add(StateEffect("childEffect", false))

        val expected = """
            ---
            config:
              layout: elk
            ---
            stateDiagram-v2
                state Parent {
                    Child
                }
                Done
                Parent --> Done: ParentEvent
                Child --> Parent: ChildEvent
                note right of Child
                    childEffect﹙﹚
                end note
        """.trimIndent()

        assertEquals(expected, MermaidWriter.toMermaid(graph).trim())
    }

    @Test
    fun `toMermaid disambiguates duplicate flat state names for edges and notes`() {
        val graph = Graph("TestGraph")
        graph.declareState("first.Child", "Child", null)
        graph.declareState("second.Child", "Child", null)
        graph.edges.add(Edge("first.Child", "Move", "second.Child"))
        graph.effects.getOrPut("second.Child") { mutableListOf() }
            .add(StateEffect("notify", false))

        val mermaid = MermaidWriter.toMermaid(graph)

        kotlin.test.assertTrue(mermaid.contains("\"Child\" as Child_first_2e_Child"))
        kotlin.test.assertTrue(mermaid.contains("\"Child\" as Child_second_2e_Child"))
        kotlin.test.assertTrue(
            mermaid.contains("Child_first_2e_Child --> Child_second_2e_Child: Move")
        )
        kotlin.test.assertTrue(mermaid.contains("note right of Child_second_2e_Child"))
    }
}
