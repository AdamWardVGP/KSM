package coffee.adammakes.ksm.ir

import coffee.adammakes.ksm.ir.model.Edge
import coffee.adammakes.ksm.ir.model.Graph
import coffee.adammakes.ksm.ir.model.StateDeclaration
import coffee.adammakes.ksm.ir.model.StateEffect
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import java.io.File

/**
 * Step 2:
 * Implement our IrGenerationExtension which searches files and calls our visitor.
 */
class KsmIrGenerationExtension(private val outputDirPath: String?) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val graphs = mutableMapOf<String, Graph>()
        val visitor = KsmIrVisitor(pluginContext, graphs)
        moduleFragment.files.forEach { file -> file.accept(visitor, null) }

        val outputDir =
            outputDirPath?.let { File(it) } ?: File(System.getProperty("user.home"), "ksmGraphs")
        outputDir.mkdirs()

        graphs.values.forEach { graph ->
            val mermaidFile = File(outputDir, "stateMachine_${graph.name}.mmd")
            val mermaidOut = MermaidWriter.toMermaid(graph)
            logger?.report(CompilerMessageSeverity.INFO, "Mermaid output:\n$mermaidOut")
            mermaidFile.writeText(mermaidOut)

            val glyphicFile = File(outputDir, "stateMachine_${graph.name}.json")
            val glyphicOut = GlyphicWriter.toGlyphic(graph)
            logger?.report(CompilerMessageSeverity.INFO, "Glyphic output:\n$glyphicOut")
            glyphicFile.writeText(glyphicOut)
        }
    }
}

class KsmIrVisitor(
    private val context: IrPluginContext,
    private val graphs: MutableMap<String, Graph>,
) : IrVisitorVoid() {

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitCall(expression: IrCall) {
        super.visitCall(expression)
        when (expression.symbol.owner.name.asString()) {
            "stateMachine" -> handleStateMachine(expression)
            "withEffects" -> handleWithEffects(expression)
        }
    }

    private fun handleStateMachine(call: IrCall) {
        val stateType = call.typeArguments.firstOrNull() ?: return
        val fqn = stateType.render()
        val graph = Graph(stateType.diagramName())
        graphs[fqn] = graph
        logger?.report(CompilerMessageSeverity.INFO, "KSM: found stateMachine for $fqn")
        val lambda = call.arguments.filterIsInstance<IrFunctionExpression>().firstOrNull()
        lambda?.function?.body?.accept(StateMachineDslVisitor(graph), null)
    }

    private fun handleWithEffects(call: IrCall) {
        val fqn = call.typeArguments.firstOrNull()?.render() ?: return
        val graph = graphs[fqn] ?: return
        logger?.report(CompilerMessageSeverity.INFO, "KSM: found withEffects for $fqn")
        val lambda = call.arguments.filterIsInstance<IrFunctionExpression>().firstOrNull()
        lambda?.function?.body?.accept(EffectContributorDslVisitor(graph), null)
    }
}

class StateMachineDslVisitor(private val graph: Graph) : IrVisitorVoid() {

    private var parentStateId: String? = null

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitCall(expression: IrCall) {
        when (expression.symbol.owner.name.asString()) {
            "state" -> {
                val state = expression.typeArguments.firstOrNull()?.stateType()
                    ?: StateType("UnknownState", "UnknownState")
                val previousParent = parentStateId
                graph.declareState(state.id, state.name, previousParent)
                logger?.report(CompilerMessageSeverity.INFO, "KSM: state [${state.name}]")

                parentStateId = state.id
                expression.arguments
                    .filterIsInstance<IrFunctionExpression>()
                    .firstOrNull()
                    ?.function
                    ?.body
                    ?.accept(this, null)
                parentStateId = previousParent
                return
            }
            "transitionTo" -> {
                val target = expression.arguments.filterNotNull().lastOrNull()?.type?.stateType()
                    ?: StateType("UnknownTarget", "UnknownTarget")
                addTransition(expression, target)
            }
            "transitionWith" -> {
                val target = expression.typeArguments.firstOrNull()?.stateType()
                    ?: StateType("UnknownTarget", "UnknownTarget")
                addTransition(expression, target)
            }
        }
        super.visitCall(expression)
    }

    private fun addTransition(expression: IrCall, target: StateType) {
        val from = parentStateId ?: return
        val event = expression.findTypeArgument("on")?.classHierarchyName() ?: "UnknownEvent"
        graph.referenceState(target.id, target.name)
        graph.edges.add(Edge(from = from, to = target.id, event = event))
        logger?.report(
            CompilerMessageSeverity.INFO,
            "KSM: transition [$from] --[$event]--> [${target.name}]",
        )
    }
}

class EffectContributorDslVisitor(private val graph: Graph) : IrVisitorVoid() {

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitCall(expression: IrCall) {
        when (expression.symbol.owner.name.asString()) {
            "effect" -> {
                val state = expression.findTypeArgument("onEnter")?.stateType()
                    ?: StateType("UnknownState", "UnknownState")
                val bodyArg = expression.arguments.lastOrNull()
                val effectName =
                    (bodyArg as? IrFunctionReference)?.symbol?.owner?.name?.asString() ?: "λ"
                logger?.report(
                    CompilerMessageSeverity.INFO,
                    "KSM effects: effect[$effectName] for state [${state.name}]",
                )
                graph.referenceState(state.id, state.name)
                graph.effects.getOrPut(state.id) { mutableListOf() }
                    .add(StateEffect(effectName, false))
            }
        }
        super.visitCall(expression)
    }
}

object MermaidWriter {

    fun toMermaid(graph: Graph): String {
        val sb = StringBuilder()
        sb.appendLine("---")
        sb.appendLine("config:")
        sb.appendLine("  layout: elk")
        sb.appendLine("---")
        sb.appendLine("stateDiagram-v2")

        val hasHierarchy = graph.stateDeclarations.values.any { it.parentId != null }
        val hasDuplicateStateNames = graph.stateNames.values
            .groupingBy { it.substringAfterLast(".") }
            .eachCount()
            .values
            .any { it > 1 }
        val useIdentifiers = hasHierarchy || hasDuplicateStateNames
        val identifiers = if (useIdentifiers) stateIdentifiers(graph) else emptyMap()

        if (hasHierarchy) {
            val children = graph.stateDeclarations.values.groupBy { it.parentId }
            for (state in children[null].orEmpty()) {
                appendState(sb, state.id, graph, children, identifiers, 1)
            }
            for ((stateId, name) in graph.stateNames) {
                if (stateId !in graph.stateDeclarations) {
                    val identifier = identifiers.getValue(stateId)
                    val label = name.substringAfterLast(".")
                    appendSimpleState(sb, identifier, label, 1)
                }
            }
        } else if (hasDuplicateStateNames) {
            for ((stateId, name) in graph.stateNames) {
                val identifier = identifiers.getValue(stateId)
                val label = name.substringAfterLast(".")
                appendSimpleState(sb, identifier, label, 1)
            }
        }

        for (edge in graph.edges) {
            val from = if (useIdentifiers) identifiers[edge.from] ?: edge.from else graph.stateName(edge.from)
            val to = if (useIdentifiers) identifiers[edge.to] ?: edge.to else graph.stateName(edge.to)
            sb.appendLine("    $from --> $to: ${edge.event}")
        }

        for ((state, effects) in graph.effects) {
            val stateId = if (useIdentifiers) identifiers[state] ?: state else graph.stateName(state)
            sb.appendLine("    note right of $stateId")
            for (effect in effects) {
                val cancelStr = if (effect.hasCancel) " ↩" else ""
                sb.appendLine("        ${effect.name}﹙﹚$cancelStr")
            }
            sb.appendLine("    end note")
        }

        return sb.toString()
    }

    private fun appendState(
        output: StringBuilder,
        stateId: String,
        graph: Graph,
        children: Map<String?, List<StateDeclaration>>,
        identifiers: Map<String, String>,
        depth: Int,
    ) {
        val state = graph.stateDeclarations.getValue(stateId)
        val nested = children[stateId].orEmpty()
        val indent = "    ".repeat(depth)
        val identifier = identifiers.getValue(stateId)
        val label = state.name.substringAfterLast(".")

        if (nested.isEmpty()) {
            appendSimpleState(output, identifier, label, depth)
            return
        }

        val declaration = if (identifier == label) identifier else "\"$label\" as $identifier"
        output.appendLine("${indent}state $declaration {")
        nested.forEach { appendState(output, it.id, graph, children, identifiers, depth + 1) }
        output.appendLine("$indent}")
    }

    private fun appendSimpleState(
        output: StringBuilder,
        identifier: String,
        label: String,
        depth: Int,
    ) {
        val indent = "    ".repeat(depth)
        if (identifier == label) {
            output.appendLine("$indent$identifier")
        } else {
            output.appendLine("${indent}state \"$label\" as $identifier")
        }
    }

    private fun stateIdentifiers(graph: Graph): Map<String, String> {
        val bases = graph.stateNames.mapValues { (_, name) ->
            name.replace(".", "_").replace(Regex("[^A-Za-z0-9_]"), "_")
                .let { if (it.firstOrNull()?.isDigit() == true) "state_$it" else it }
        }
        val duplicateBases = bases.values.groupingBy { it }.eachCount()
        return bases.mapValues { (id, base) ->
            if (duplicateBases.getValue(base) == 1) base else "${base}_${encodeIdentifier(id)}"
        }
    }

    private fun encodeIdentifier(value: String): String = buildString {
        value.forEach { character ->
            if (character.isLetterOrDigit()) {
                append(character)
            } else {
                append('_')
                append(character.code.toString(16))
                append('_')
            }
        }
    }
}

/**
 * Emits state graphs as [Glyphic](https://github.com/MS-Teja/Glyphic) `"type": "state"` diagram
 * documents — a strict JSON schema Glyphic renders to SVG/PNG without a browser.
 *
 * Glyphic node ids must match `^[a-zA-Z0-9_-]+$`, so the dotted fully-qualified state ids KSM
 * tracks internally (e.g. `com.app.AppState.Login`) are sanitized into that alphabet, only
 * falling back to a longer disambiguated form on collision. The human-readable class name is
 * kept separate as `label`. Every state referenced by an edge or effect must appear in
 * `states[]` (Glyphic validates this), so all known states are always emitted.
 *
 * Glyphic has no note/annotation primitive for state diagrams and gives every line of a node's
 * label the same styling, so an effect can't just be a second line on its owning state's label
 * without reading as more of that state's name. Instead each effect becomes its own small node —
 * marked with a ⚡ prefix and reached by an "on enter" edge from its owning state — mirroring how
 * Mermaid renders `withEffects` as a visually distinct note bubble off to the side.
 */
object GlyphicWriter {

    fun toGlyphic(graph: Graph): String {
        val children = graph.stateDeclarations.values.groupBy { it.parentId }
        val compositeIds = graph.stateDeclarations.values.mapNotNull { it.parentId }.toSet()

        val order = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val effectNodes = mutableListOf<EffectNode>()
        val effectTransitions = mutableListOf<EffectTransition>()

        fun addEffectNodes(stateId: String) {
            val effects = graph.effects[stateId] ?: return
            val parentId = graph.stateDeclarations[stateId]?.parentId
            effects.forEachIndexed { index, effect ->
                val effectId = "$stateId effect $index"
                val cancelStr = if (effect.hasCancel) " ↩" else ""
                effectNodes += EffectNode(effectId, "⚡ ${effect.name}﹙﹚$cancelStr", parentId)
                effectTransitions += EffectTransition(stateId, effectId)
                order += effectId
                visited += effectId
            }
        }

        fun visit(stateId: String) {
            if (!visited.add(stateId)) return
            order += stateId
            children[stateId].orEmpty().forEach { visit(it.id) }
            addEffectNodes(stateId)
        }

        children[null].orEmpty().forEach { visit(it.id) }
        graph.stateNames.keys.forEach(::visit)
        // Edge endpoints and effect targets may reference states that were never explicitly
        // declared (e.g. a transitionTo target with no matching `state<T> { }` block). Glyphic
        // requires every id used by a transition to appear in `states[]`, so backfill those too.
        graph.edges.forEach { visit(it.from); visit(it.to) }
        graph.effects.keys.forEach(::visit)

        val identifiers = disambiguatedIdentifiers(order)
        val effectLabels = effectNodes.associate { it.id to it.label }
        val effectParents = effectNodes.associate { it.id to it.parentId }

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("""  "type": "state",""")
        sb.appendLine("""  "title": "${escapeJson(graph.name)}",""")
        sb.appendLine("""  "direction": "TB",""")
        sb.appendLine("""  "states": [""")
        order.forEachIndexed { index, stateId ->
            val label = effectLabels[stateId] ?: graph.stateName(stateId).substringAfterLast(".")
            val parentId = if (stateId in effectLabels) effectParents.getValue(stateId) else {
                graph.stateDeclarations[stateId]?.parentId
            }
            sb.append(
                """    { "id": "${identifiers.getValue(stateId)}", "label": "${escapeJson(label)}""""
            )
            if (stateId in compositeIds) sb.append(""", "kind": "composite"""")
            parentId?.let { sb.append(""", "parent": "${identifiers.getValue(it)}"""") }
            sb.append(" }")
            sb.appendLine(if (index != order.lastIndex) "," else "")
        }
        sb.appendLine("  ],")
        sb.appendLine("""  "transitions": [""")
        val allTransitions = graph.edges.map { it.from to it.to to it.event } +
            effectTransitions.map { (it.from to it.to) to "on enter" }
        allTransitions.forEachIndexed { index, (endpoints, label) ->
            val (from, to) = endpoints
            sb.append(
                """    { "from": "${identifiers.getValue(from)}", """ +
                    """"to": "${identifiers.getValue(to)}", "label": "${escapeJson(label)}" }"""
            )
            sb.appendLine(if (index != allTransitions.lastIndex) "," else "")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    private data class EffectNode(val id: String, val label: String, val parentId: String?)
    private data class EffectTransition(val from: String, val to: String)

    /** Sanitizes each id into Glyphic's `[a-zA-Z0-9_-]` alphabet, disambiguating collisions. */
    private fun disambiguatedIdentifiers(ids: List<String>): Map<String, String> {
        val bases = ids.associateWith { sanitizeIdentifier(it) }
        val duplicateBases = bases.values.groupingBy { it }.eachCount()
        return ids.associateWith { id ->
            val base = bases.getValue(id)
            if (duplicateBases.getValue(base) == 1) base else "${base}_${encodeIdentifier(id)}"
        }
    }

    private fun sanitizeIdentifier(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "state" }

    private fun encodeIdentifier(value: String): String = buildString {
        value.forEach { character ->
            if (character.isLetterOrDigit()) {
                append(character)
            } else {
                append('_')
                append(character.code.toString(16))
                append('_')
            }
        }
    }

    private fun escapeJson(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                else -> append(character)
            }
        }
    }
}

private data class StateType(val id: String, val name: String)

private fun IrType.diagramName(): String {
    val owner = (this as? IrSimpleType)?.classifierOrNull?.owner
    return (owner as? IrDeclarationWithName)?.name?.asString() ?: render().substringAfterLast(".")
}

private fun IrCall.findTypeArgument(callName: String): IrType? {
    var result: IrType? = null
    acceptChildrenVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                if (result == null) element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                if (expression.symbol.owner.name.asString() == callName) {
                    result = expression.typeArguments.firstOrNull()
                } else {
                    super.visitCall(expression)
                }
            }
        }
    )
    return result
}

private fun IrType.stateType(): StateType {
    val owner = (this as? IrSimpleType)?.classifierOrNull?.owner
    val id = (owner as? IrDeclarationWithName)?.fqNameWhenAvailable?.asString() ?: render()
    return StateType(id, classHierarchyName())
}

/**
 * Returns the class name including hierarchy within its containing class, but excluding the
 * package and top-level sealed class name. Nested separators use . to show hierarchy.
 *
 * Examples (assuming top-level sealed class is stripped):
 *   AdventureState.Start        → "Start"
 *   GameState.Combat.Fighting   → "Combat.Fighting"
 */
private fun IrType.classHierarchyName(): String {
    val owner = (this as? IrSimpleType)?.classifierOrNull?.owner
    if (owner !is IrDeclarationWithName) return this.render().split(".").last()

    val names = mutableListOf<String>()
    var current: Any? = owner
    while (current is IrDeclarationWithName) {
        names.add(0, current.name.asString())
        val parent = (current as? IrDeclaration)?.parent
        if (parent == null || parent is IrPackageFragment) break
        current = parent
    }
    if (names.size > 1) names.removeAt(0)
    return names.joinToString(".")
}
