package coffee.adammakes.ksm.ir

import coffee.adammakes.ksm.ir.model.Edge
import coffee.adammakes.ksm.ir.model.Graph
import coffee.adammakes.ksm.ir.model.StateEffect
import org.junit.Test
import kotlin.test.assertEquals

class GlyphicWriterTest {
    @Test
    fun `toGlyphic produces correct output`() {
        val graph = Graph("TestGraph")
        graph.edges.add(Edge("Initial", "Move", "Final"))
        graph.edges.add(Edge("Final", "Reset", "Initial"))

        val expected = """
            {
              "type": "state",
              "title": "TestGraph",
              "direction": "TB",
              "states": [
                { "id": "Initial", "label": "Initial" },
                { "id": "Final", "label": "Final" }
              ],
              "transitions": [
                { "from": "Initial", "to": "Final", "label": "Move" },
                { "from": "Final", "to": "Initial", "label": "Reset" }
              ]
            }
        """.trimIndent()

        val actual = GlyphicWriter.toGlyphic(graph).trim()
        assertEquals(expected, actual)
    }

    @Test
    fun `toGlyphic renders effects as separate nodes marked with a lightning bolt`() {
        val graph = Graph("TestGraph")
        graph.effects.getOrPut("Final") { mutableListOf() }.add(StateEffect("rainCoins", false))
        graph.effects.getOrPut("Final") { mutableListOf() }.add(StateEffect("stopMusic", true))

        val expected = """
            {
              "type": "state",
              "title": "TestGraph",
              "direction": "TB",
              "states": [
                { "id": "Final", "label": "Final" },
                { "id": "Final_effect_0", "label": "⚡ rainCoins﹙﹚" },
                { "id": "Final_effect_1", "label": "⚡ stopMusic﹙﹚ ↩" }
              ],
              "transitions": [
                { "from": "Final", "to": "Final_effect_0", "label": "on enter" },
                { "from": "Final", "to": "Final_effect_1", "label": "on enter" }
              ]
            }
        """.trimIndent()

        val actual = GlyphicWriter.toGlyphic(graph).trim()
        assertEquals(expected, actual)
    }

    @Test
    fun `toGlyphic renders DSL hierarchy with scoped transitions and effects`() {
        val graph = Graph("TestGraph")
        graph.declareState("test.Parent", "Parent", null)
        graph.declareState("other.Child", "Child", "test.Parent")
        graph.declareState("test.Done", "Done", null)
        graph.edges.add(Edge("test.Parent", "ParentEvent", "test.Done"))
        graph.edges.add(Edge("other.Child", "ChildEvent", "test.Parent"))
        graph.effects.getOrPut("other.Child") { mutableListOf() }
            .add(StateEffect("childEffect", false))

        val expected = """
            {
              "type": "state",
              "title": "TestGraph",
              "direction": "TB",
              "states": [
                { "id": "test_Parent", "label": "Parent", "kind": "composite" },
                { "id": "other_Child", "label": "Child", "parent": "test_Parent" },
                { "id": "other_Child_effect_0", "label": "⚡ childEffect﹙﹚", "parent": "test_Parent" },
                { "id": "test_Done", "label": "Done" }
              ],
              "transitions": [
                { "from": "test_Parent", "to": "test_Done", "label": "ParentEvent" },
                { "from": "other_Child", "to": "test_Parent", "label": "ChildEvent" },
                { "from": "other_Child", "to": "other_Child_effect_0", "label": "on enter" }
              ]
            }
        """.trimIndent()

        assertEquals(expected, GlyphicWriter.toGlyphic(graph).trim())
    }

    @Test
    fun `toGlyphic keeps duplicate flat state labels distinct by id`() {
        val graph = Graph("TestGraph")
        graph.declareState("first.Child", "Child", null)
        graph.declareState("second.Child", "Child", null)
        graph.edges.add(Edge("first.Child", "Move", "second.Child"))
        graph.effects.getOrPut("second.Child") { mutableListOf() }
            .add(StateEffect("notify", false))

        val glyphic = GlyphicWriter.toGlyphic(graph)

        kotlin.test.assertTrue(glyphic.contains(""""id": "first_Child", "label": "Child" }"""))
        kotlin.test.assertTrue(glyphic.contains(""""id": "second_Child", "label": "Child" }"""))
        kotlin.test.assertTrue(
            glyphic.contains(""""id": "second_Child_effect_0", "label": "⚡ notify﹙﹚" }""")
        )
        kotlin.test.assertTrue(
            glyphic.contains(""""from": "first_Child", "to": "second_Child", "label": "Move"""")
        )
        kotlin.test.assertTrue(
            glyphic.contains(
                """"from": "second_Child", "to": "second_Child_effect_0", "label": "on enter""""
            )
        )
    }

    @Test
    fun `toGlyphic disambiguates ids that sanitize to the same value`() {
        val graph = Graph("TestGraph")
        graph.declareState("a.b", "First", null)
        graph.declareState("a_b", "Second", null)
        graph.edges.add(Edge("a.b", "Move", "a_b"))

        val glyphic = GlyphicWriter.toGlyphic(graph)

        // Both "a.b" and "a_b" sanitize to the base "a_b", so both fall back to a disambiguated
        // id built from their original (unsanitized) source id, keeping every id a valid Glyphic
        // identifier while remaining collision-free.
        kotlin.test.assertTrue(glyphic.contains(""""id": "a_b_a_2e_b""""), glyphic)
        kotlin.test.assertTrue(glyphic.contains(""""id": "a_b_a_5f_b""""), glyphic)
        kotlin.test.assertTrue(
            glyphic.contains(""""from": "a_b_a_2e_b", "to": "a_b_a_5f_b", "label": "Move"""")
        )
    }
}
