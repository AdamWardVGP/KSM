package coffee.adammakes.ksm.ir

import coffee.adammakes.ksm.ir.model.Edge
import coffee.adammakes.ksm.ir.model.Graph
import coffee.adammakes.ksm.ir.model.StateEffect
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `toMermaid renders the initial-state marker for any graph`() {
        val graph = Graph("TestGraph")
        graph.edges.add(Edge("Initial", "Move", "Final"))
        graph.initialStateId = "Initial"

        val expected = """
            ---
            config:
              layout: elk
            ---
            stateDiagram-v2
                [*] --> Initial
                Initial --> Final: Move
        """.trimIndent()

        assertEquals(expected, MermaidWriter.toMermaid(graph).trim())
    }

    @Test
    fun `toMermaid leaves a non-composite graph unwrapped`() {
        val graph = Graph("TestGraph")
        graph.edges.add(Edge("Initial", "Move", "Final"))

        val mermaid = MermaidWriter.toMermaid(graph)

        assertFalse(mermaid.contains("as main_box"))
    }

    @Test
    fun `toMermaid skips the outer wrap when the graph also has same-typed hierarchical nesting`() {
        // Regression test: wrapping a graph that already nests a hierarchical child two levels
        // deep, combined with an edge that reaches from a sibling straight into that nested
        // child, produces a diagram real mermaid renderers fail to lay out (verified by hand
        // against the actual renderer). Skip the wrap rather than ship an unrenderable diagram.
        val parent = Graph("AppState")
        parent.declareState("app.Idle", "Idle", null)
        parent.declareState("app.UpdateFlow", "UpdateFlow", null)
        parent.declareState("app.Finished", "Finished", null)
        parent.declareState("app.Done", "Done", "app.Finished")
        parent.edges.add(Edge("app.Idle", "StartUpdate", "app.UpdateFlow"))
        parent.edges.add(Edge("app.Idle", "Skip", "app.Done"))
        parent.declareComposite("app.UpdateFlow", "child.UpdateState")

        val child = Graph("UpdateState")
        child.declareState("child.Checking", "Checking", null)

        val mermaid = MermaidWriter.toMermaid(parent, mapOf("child.UpdateState" to child))

        assertFalse(mermaid.contains("as main_box"))
        assertTrue(mermaid.contains("state \"UpdateFlow\" as child_box_"))
    }

    @Test
    fun `toMermaid renders a composite state as a collapsed node plus a child box and exit-wiring edges`() {
        val parent = Graph("AppState")
        parent.declareState("app.Idle", "Idle", null)
        parent.declareState("app.UpdateFlow", "UpdateFlow", null)
        parent.declareState("app.Done", "Done", null)
        parent.edges.add(Edge("app.Idle", "StartUpdate", "app.UpdateFlow"))
        parent.edges.add(Edge("app.UpdateFlow", "UpdateFinished", "app.Done"))
        parent.declareComposite("app.UpdateFlow", "child.UpdateState")
        parent.addExitWiring("app.UpdateFlow", "child.Done", "Done", "UpdateFinished")

        val child = Graph("UpdateState")
        child.declareState("child.Checking", "Checking", null)
        child.declareState("child.Done", "Done", null)
        child.edges.add(Edge("child.Checking", "Go", "child.Done"))

        val mermaid = MermaidWriter.toMermaid(parent, mapOf("child.UpdateState" to child))

        // Parent's own states render boxed, separate from the composite/exit boxes.
        assertTrue(mermaid.contains("state \"AppState\" as main_box {"))
        assertTrue(mermaid.contains("Idle --> UpdateFlow: StartUpdate"))
        assertFalse(mermaid.contains("state UpdateFlow {"))

        // The exit-wired transition is shown once (via the exit box), not duplicated here.
        assertFalse(mermaid.contains("UpdateFlow --> Done: UpdateFinished"))

        // Child box is labelled with the owning composite state's name, not the child FSM's own name.
        assertTrue(mermaid.contains("state \"UpdateFlow\" as child_box_app_2e_UpdateFlow {"))
        assertTrue(mermaid.contains("child_Checking"))
        assertTrue(mermaid.contains("child_Done"))
        assertTrue(mermaid.contains("child_Checking --> child_Done: Go"))

        // Mirror box of the parent state(s) exit wiring targets, with the exit edge.
        assertTrue(mermaid.contains("state \"Exit targets\" as exit_box_app_2e_UpdateFlow {"))
        assertTrue(mermaid.contains("\"Done\" as exit_app_2e_Done"))
        assertTrue(mermaid.contains("child_Done --> exit_app_2e_Done: UpdateFinished"))

        // Uniform role-based stroke classes: blue for the child region, green for exit targets.
        assertTrue(mermaid.contains("classDef compositeChild stroke:#1565c0"))
        assertTrue(mermaid.contains("classDef exitTarget stroke:#2e7d32"))
        assertTrue(mermaid.contains("class child_box_app_2e_UpdateFlow"))
        assertTrue(mermaid.contains("compositeChild"))
        assertTrue(mermaid.contains("class exit_box_app_2e_UpdateFlow"))
        assertTrue(mermaid.contains("exitTarget"))
    }

    @Test
    fun `toMermaid falls back to the event name when exit wiring has no matching transition`() {
        val parent = Graph("AppState")
        parent.declareState("app.UpdateFlow", "UpdateFlow", null)
        parent.declareComposite("app.UpdateFlow", "child.UpdateState")
        parent.addExitWiring("app.UpdateFlow", "child.Done", "Done", "Unwired")

        val child = Graph("UpdateState")
        child.declareState("child.Done", "Done", null)

        val mermaid = MermaidWriter.toMermaid(parent, mapOf("child.UpdateState" to child))

        kotlin.test.assertTrue(mermaid.contains("\"Unwired\" as"))
    }

    @Test
    fun `toMermaid renders nothing extra for a missing child graph`() {
        val parent = Graph("AppState")
        parent.declareState("app.UpdateFlow", "UpdateFlow", null)
        parent.declareComposite("app.UpdateFlow", "child.Missing")

        val mermaid = MermaidWriter.toMermaid(parent, emptyMap())

        kotlin.test.assertFalse(mermaid.contains("child_box"))
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
