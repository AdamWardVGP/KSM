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
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
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
            val file = File(outputDir, "stateMachine_${graph.name}.mmd")
            val mermaidOut = MermaidWriter.toMermaid(graph, graphs)
            logger?.report(CompilerMessageSeverity.INFO, "Mermaid output:\n$mermaidOut")
            file.writeText(mermaidOut)
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
            "<set-initialState>" -> {
                val initial = expression.arguments.filterNotNull().lastOrNull()?.resolveStateType()
                if (initial != null) {
                    graph.referenceState(initial.id, initial.name)
                    graph.initialStateId = initial.id
                }
            }
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
            "child" -> {
                handleChild(expression)
                // Deliberately skip the default recursion below: the factory lambda's own
                // stateMachine{} call is discovered independently (see KsmIrGenerationExtension's
                // module-wide walk) and must NOT be inlined into this graph.
                return
            }
        }
        super.visitCall(expression)
    }

    private fun handleChild(expression: IrCall) {
        val owner = parentStateId ?: return
        val childStateType = expression.typeArguments.getOrNull(0) ?: return
        graph.declareComposite(owner, childStateType.render())
        logger?.report(
            CompilerMessageSeverity.INFO,
            "KSM: composite child [${childStateType.render()}] embedded in [$owner]",
        )

        // Second function-typed argument is the exit-wiring block; the first is the child
        // factory, which we must not recurse into (see the comment at the call site).
        expression.arguments.filterIsInstance<IrFunctionExpression>().getOrNull(1)
            ?.function?.body?.accept(ExitWiringDslVisitor(graph, owner), null)
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

/** Visits an exit-wiring block (`child(...) { exit<X> { event } }`), recording each `exit<>`. */
class ExitWiringDslVisitor(private val graph: Graph, private val ownerId: String) : IrVisitorVoid() {

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitCall(expression: IrCall) {
        if (expression.symbol.owner.name.asString() == "exit") {
            val exitState = expression.typeArguments.firstOrNull()?.stateType()
            val eventName = expression.arguments.filterIsInstance<IrFunctionExpression>()
                .firstOrNull()?.singleReturnType()?.classHierarchyName() ?: "UnknownEvent"
            if (exitState != null) {
                graph.addExitWiring(ownerId, exitState.id, exitState.name, eventName)
                logger?.report(
                    CompilerMessageSeverity.INFO,
                    "KSM: exit wiring [${exitState.name}] --[$eventName]--> parent",
                )
            }
        }
        super.visitCall(expression)
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

    /**
     * [allGraphs] resolves a composite's child graph by the key recorded in
     * [Graph.composites] — pass the full set of graphs discovered in the module so composite
     * children can be looked up. Defaults to empty for graphs with no composites.
     */
    fun toMermaid(graph: Graph, allGraphs: Map<String, Graph> = emptyMap()): String {
        val display = mainDisplay(graph)
        // Wrapping the main graph in its own outer box adds a second level of compound nesting.
        // Combined with same-typed hierarchical nesting (which already nests one level) plus an
        // edge that reaches past a sibling into a nested state's child, this trips a real bug in
        // mermaid's layout engine (verified by hand against the actual renderer) — so skip the
        // wrap for graphs that already have hierarchy, rather than risk an unrenderable diagram.
        val shouldWrapMainBox = graph.composites.isNotEmpty() && !display.hasHierarchy

        val sb = StringBuilder()
        sb.appendLine("---")
        sb.appendLine("config:")
        sb.appendLine("  layout: elk")
        sb.appendLine("---")
        sb.appendLine("stateDiagram-v2")

        val mainContent = buildMainContent(graph, display)
        if (shouldWrapMainBox) {
            sb.appendLine("    state \"${graph.name}\" as main_box {")
            sb.append(indented(mainContent))
            sb.appendLine("    }")
        } else {
            sb.append(mainContent)
        }

        if (graph.composites.isNotEmpty()) {
            val blueIds = mutableListOf<String>()
            val greenIds = mutableListOf<String>()
            appendComposites(sb, graph, allGraphs, blueIds, greenIds, display)
            appendStrokeClasses(sb, blueIds, greenIds)
        }

        return sb.toString()
    }

    /** Resolves a state id to how it's referenced in the main graph's own content — either its
     * bare name, or a sanitized identifier when hierarchy/duplicate names force disambiguation. */
    private class MainDisplay(
        val hasHierarchy: Boolean,
        val useIdentifiers: Boolean,
        val identifiers: Map<String, String>,
        private val graph: Graph,
    ) {
        fun resolve(id: String): String = if (useIdentifiers) identifiers[id] ?: id else graph.stateName(id)
    }

    private fun mainDisplay(graph: Graph): MainDisplay {
        val hasHierarchy = graph.stateDeclarations.values.any { it.parentId != null }
        val hasDuplicateStateNames = graph.stateNames.values
            .groupingBy { it.substringAfterLast(".") }
            .eachCount()
            .values
            .any { it > 1 }
        val useIdentifiers = hasHierarchy || hasDuplicateStateNames
        val identifiers = if (useIdentifiers) stateIdentifiers(graph) else emptyMap()
        return MainDisplay(hasHierarchy, useIdentifiers, identifiers, graph)
    }

    /** Everything that renders at the top level of a graph: states, initial marker, edges, effects. */
    private fun buildMainContent(graph: Graph, display: MainDisplay): String {
        val sb = StringBuilder()

        if (display.hasHierarchy) {
            val children = graph.stateDeclarations.values.groupBy { it.parentId }
            for (state in children[null].orEmpty()) {
                appendState(sb, state.id, graph, children, display.identifiers, 1)
            }
            for ((stateId, name) in graph.stateNames) {
                if (stateId !in graph.stateDeclarations) {
                    val identifier = display.identifiers.getValue(stateId)
                    val label = name.substringAfterLast(".")
                    appendSimpleState(sb, identifier, label, 1)
                }
            }
        } else if (display.useIdentifiers) {
            for ((stateId, name) in graph.stateNames) {
                val identifier = display.identifiers.getValue(stateId)
                val label = name.substringAfterLast(".")
                appendSimpleState(sb, identifier, label, 1)
            }
        }

        graph.initialStateId?.let { initial -> sb.appendLine("    [*] --> ${display.resolve(initial)}") }

        val exitWiredTransitions = graph.composites.values
            .flatMapTo(mutableSetOf()) { declaration ->
                declaration.exitWiring.map { declaration.ownerId to it.parentEventName }
            }
        for (edge in graph.edges) {
            if (edge.from to edge.event in exitWiredTransitions) continue
            sb.appendLine("    ${display.resolve(edge.from)} --> ${display.resolve(edge.to)}: ${edge.event}")
        }

        for ((state, effects) in graph.effects) {
            sb.appendLine("    note right of ${display.resolve(state)}")
            for (effect in effects) {
                val cancelStr = if (effect.hasCancel) " ↩" else ""
                sb.appendLine("        ${effect.name}﹙﹚$cancelStr")
            }
            sb.appendLine("    end note")
        }

        return sb.toString()
    }

    /** Prefixes every non-blank line of [content] with one indent level. */
    private fun indented(content: String): String =
        content.trimEnd('\n').lineSequence().joinToString("\n") { if (it.isEmpty()) it else "    $it" } + "\n"

    /**
     * Renders each composite's child FSM as a separate box (its real states, expanded) alongside
     * a mirror box of the parent states exit wiring targets, connected by the exit-wiring edges.
     * The composite's owner state itself was already rendered in [buildMainContent] as a plain
     * leaf node — no child internals are inlined there, but its main-graph id is still added to
     * [blueIds] so it carries the same stroke as its expanded child box. Likewise, any exit-wiring
     * target that resolves to a real main-graph state is added to [greenIds].
     */
    private fun appendComposites(
        output: StringBuilder,
        graph: Graph,
        allGraphs: Map<String, Graph>,
        blueIds: MutableList<String>,
        greenIds: MutableList<String>,
        display: MainDisplay,
    ) {
        for (declaration in graph.composites.values) {
            val childGraph = allGraphs[declaration.childGraphKey] ?: continue
            val childBaseIdentifiers = stateIdentifiers(childGraph)
            val childIdentifiers = childBaseIdentifiers.mapValues { (_, id) -> "child_$id" }
            val childChildren = childGraph.stateDeclarations.values.groupBy { it.parentId }
            val ownerToken = encodeIdentifier(declaration.ownerId)
            val ownerLabel = graph.stateName(declaration.ownerId)
            val childBoxId = "child_box_$ownerToken"

            output.appendLine()
            output.appendLine("    state \"$ownerLabel\" as $childBoxId {")
            for (state in childChildren[null].orEmpty()) {
                appendState(output, state.id, childGraph, childChildren, childIdentifiers, 2)
            }
            for ((stateId, name) in childGraph.stateNames) {
                if (stateId !in childGraph.stateDeclarations) {
                    val identifier = childIdentifiers.getValue(stateId)
                    appendSimpleState(output, identifier, name.substringAfterLast("."), 2)
                }
            }
            childGraph.initialStateId?.let { initial ->
                val identifier = childIdentifiers[initial] ?: initial
                output.appendLine("        [*] --> $identifier")
            }
            for (edge in childGraph.edges) {
                val from = childIdentifiers[edge.from] ?: edge.from
                val to = childIdentifiers[edge.to] ?: edge.to
                output.appendLine("        $from --> $to: ${edge.event}")
            }
            output.appendLine("    }")
            blueIds += childBoxId
            blueIds += childIdentifiers.values
            blueIds += display.resolve(declaration.ownerId)

            if (declaration.exitWiring.isEmpty()) continue

            val exitBoxId = "exit_box_$ownerToken"
            output.appendLine("    state \"Exit targets\" as $exitBoxId {")
            val mirrored = linkedMapOf<String, String>()
            val mainGraphTargets = mutableSetOf<String>()
            val exitEdges = mutableListOf<String>()
            for (exit in declaration.exitWiring) {
                val targetEdge = graph.edges.firstOrNull {
                    it.from == declaration.ownerId && it.event == exit.parentEventName
                }
                val targetKey = targetEdge?.to ?: "event_${exit.parentEventName}"
                val targetLabel = targetEdge?.let { graph.stateName(it.to) } ?: exit.parentEventName
                val mirrorId = mirrored.getOrPut(targetKey) {
                    val id = "exit_${encodeIdentifier(targetKey)}"
                    appendSimpleState(output, id, targetLabel, 2)
                    id
                }
                if (targetEdge != null) mainGraphTargets += display.resolve(targetEdge.to)
                val childIdentifier = childIdentifiers[exit.childStateId]
                    ?: "child_${encodeIdentifier(exit.childStateId)}"
                exitEdges.add("    $childIdentifier --> $mirrorId: ${exit.parentEventName}")
            }
            output.appendLine("    }")
            exitEdges.forEach(output::appendLine)
            greenIds += exitBoxId
            greenIds += mirrored.values
            greenIds += mainGraphTargets
        }
    }

    /** Blue stroke for composite child regions, green stroke for exit-target regions — uniform
     * across every composite in the file; box labels (not color) tell multiple composites apart. */
    private fun appendStrokeClasses(output: StringBuilder, blueIds: List<String>, greenIds: List<String>) {
        output.appendLine()
        output.appendLine("    classDef compositeChild stroke:#1565c0")
        output.appendLine("    classDef exitTarget stroke:#2e7d32")
        if (blueIds.isNotEmpty()) output.appendLine("    class ${blueIds.distinct().joinToString(",")} compositeChild")
        if (greenIds.isNotEmpty()) output.appendLine("    class ${greenIds.distinct().joinToString(",")} exitTarget")
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

/** For a single-expression lambda `{ SomeEvent }`, the static type of its returned expression. */
private fun IrFunctionExpression.singleReturnType(): IrType? {
    val body = function.body as? IrBlockBody ?: return null
    return body.statements.filterIsInstance<IrReturn>().firstOrNull()?.value?.type
}

private fun IrType.stateType(): StateType {
    val owner = (this as? IrSimpleType)?.classifierOrNull?.owner
    val id = (owner as? IrDeclarationWithName)?.fqNameWhenAvailable?.asString() ?: render()
    return StateType(id, classHierarchyName())
}

/**
 * Resolves the concrete state type an expression evaluates to. A reference to a value parameter
 * only carries its *declared* type (e.g. `initialState: AdventureState = AdventureState.Start`
 * types as the sealed base, not `Start`) — in that case, resolve through the parameter's default
 * value instead, which is where DSL factory functions typically spell out the concrete literal.
 */
private fun IrExpression.resolveStateType(): StateType {
    val param = (this as? IrGetValue)?.symbol?.owner as? IrValueParameter
    val default = param?.defaultValue?.expression
    return (default ?: this).type.stateType()
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
