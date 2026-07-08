package coffee.adammakes.ksm.ir

import coffee.adammakes.ksm.ir.model.Edge
import coffee.adammakes.ksm.ir.model.Graph
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
            val mermaidOut = MermaidWriter.toMermaid(graph)
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
        val fqn = call.typeArguments.firstOrNull()?.render() ?: return
        val graph = Graph(fqn.split(".").last())
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

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    private var currentState: String? = null
    private var currentEvent: String? = null
    private var targetState: String? = null

    private fun checkAddItems() {
        if (currentState != null && currentEvent != null && targetState != null) {
            graph.edges.add(
                Edge(
                    from = currentState ?: "UNKNOWN",
                    to = targetState ?: "UNKNOWN",
                    event = currentEvent ?: "UNKNOWN",
                )
            )
        }
    }

    override fun visitCall(expression: IrCall) {
        when (expression.symbol.owner.name.asString()) {
            "state" -> {
                val typeArg = expression.typeArguments.firstOrNull()
                currentState = typeArg?.classHierarchyName() ?: "UnknownState"
                logger?.report(CompilerMessageSeverity.INFO, "KSM: state [$currentState]")
                graph.states.add(currentState!!)
            }
            "on" -> {
                val typeArg = expression.typeArguments.firstOrNull()
                currentEvent = typeArg?.classHierarchyName() ?: "UnknownEvent"
                logger?.report(CompilerMessageSeverity.INFO, "KSM: event [$currentEvent]")
                checkAddItems()
            }
            "transitionTo" -> {
                targetState = expression.arguments[1]?.type?.classHierarchyName() ?: "UnknownTarget"
                logger?.report(CompilerMessageSeverity.INFO, "KSM: transitionTo [$targetState]")
            }
            "transitionWith" -> {
                targetState =
                    expression.typeArguments.firstOrNull()?.classHierarchyName() ?: "UnknownTarget"
                logger?.report(CompilerMessageSeverity.INFO, "KSM: transitionWith [$targetState]")
            }
        }
        super.visitCall(expression)
    }
}

class EffectContributorDslVisitor(private val graph: Graph) : IrVisitorVoid() {

    private var currentState: String? = null

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitCall(expression: IrCall) {
        // Recurse first so that when we process "effect", currentState has already been set
        // by the "onEnter" child call below.
        super.visitCall(expression)
        when (expression.symbol.owner.name.asString()) {
            "onEnter" -> {
                // IrGenerationExtension runs before inline expansion, so S::class is not yet a
                // literal IrClassReference. Read the reified type argument directly instead.
                val typeArg = expression.typeArguments.firstOrNull()
                currentState = typeArg?.classHierarchyName() ?: "UnknownState"
                logger?.report(
                    CompilerMessageSeverity.INFO,
                    "KSM effects: onEnter [$currentState]",
                )
            }
            "effect" -> {
                val state = currentState ?: "UnknownState"
                val bodyArg = expression.arguments.lastOrNull()
                val effectName =
                    (bodyArg as? IrFunctionReference)?.symbol?.owner?.name?.asString() ?: "λ"
                logger?.report(
                    CompilerMessageSeverity.INFO,
                    "KSM effects: effect[$effectName] for state [$state]",
                )
                graph.effects.getOrPut(state) { mutableListOf() }.add(StateEffect(effectName, false))
            }
        }
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

        for (edge in graph.edges) {
            sb.appendLine("    ${edge.from} --> ${edge.to}: ${edge.event}")
        }

        for ((state, effects) in graph.effects) {
            sb.appendLine("    note right of $state")
            for (effect in effects) {
                val cancelStr = if (effect.hasCancel) " ↩" else ""
                sb.appendLine("        ${effect.name}﹙﹚$cancelStr")
            }
            sb.appendLine("    end note")
        }

        return sb.toString()
    }
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
    names.removeAt(0)
    return names.joinToString(".")
}
