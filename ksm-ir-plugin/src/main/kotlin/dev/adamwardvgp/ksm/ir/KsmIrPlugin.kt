package dev.adamwardvgp.ksm.ir

import dev.adamwardvgp.ksm.ir.model.Edge
import dev.adamwardvgp.ksm.ir.model.Graph
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
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
class KsmIrGenerationExtension : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        moduleFragment.files.forEach { file ->
            file.accept(
                KsmIrVisitor(pluginContext),
                null
            )
        }
    }
}

/**
 * Step 3:
 * Inspect calls looking for methods named "stateMachine".
 *
 * Step 5:
 * Once it's been processed output the mermaid writer
 */
class KsmIrVisitor(
    private val context: IrPluginContext
) : IrVisitorVoid() {

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    private val outputDir: File by lazy {
        File(System.getProperty("user.home"), "build/ksmGraphs").apply { mkdirs() }
    }

    override fun visitCall(expression: IrCall) {
        super.visitCall(expression)

        val owner = expression.symbol.owner.name.asString()

        if (
            owner == "stateMachine" || owner == "<get-stateMachine>"
        ) {
            handleStateMachine(expression)
        }
    }


    private fun handleStateMachine(call: IrCall) {
        // stateMachine { ... }
        val lambda = call.arguments.filterIsInstance<IrFunctionExpression>().firstOrNull()

        val graphName = call.typeArguments.firstOrNull()?.render() ?: "StateMachine"
        val graph = Graph(graphName)

        logger?.report(CompilerMessageSeverity.INFO, "graphName ${graphName}")

        lambda?.function?.body?.accept(
            StateMachineDslVisitor(graph),
            null
        )

        logger?.report(CompilerMessageSeverity.INFO, "graph $graph")

        val file = File(outputDir, "stateMachine_${graph.name}.mmd")
        val mermaidOut = MermaidWriter.toMermaid(graph)
        logger?.report(CompilerMessageSeverity.INFO, "Mermaid: $mermaidOut")
        file.writeText(mermaidOut)
    }
}

/**
 * Step 4:
 * Traverse our DSL and extract entrance states, events, and target states.
 */
class StateMachineDslVisitor(
    private val graph: Graph
) : IrVisitorVoid() {

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    private var currentState: String? = null
    private var currentEvent: String? = "UNKNOWN"

    override fun visitCall(expression: IrCall) {

        when (expression.symbol.owner.name.asString()) {

            "state" -> {
                // state<T> { ... }
                val typeArg = expression.typeArguments.firstOrNull()
                currentState = typeArg?.render()?.split(".")?.last() ?: "UnknownState"

                logger?.report(CompilerMessageSeverity.INFO, "Adding state $currentState")
                graph.states.add(currentState!!)
            }

            "on" -> {
                // on<E>()
                val typeArg = expression.typeArguments.firstOrNull()
                currentEvent = typeArg?.classHierarchyName() ?: "UnknownEvent"
            }

            "transitionTo" -> {

                val target = expression.arguments[0]
                val type = target?.type
                val name = type?.classHierarchyName()
                    ?: "UnknownTarget"

                logger?.report(CompilerMessageSeverity.INFO, "DEBUG $target,    $type,    $name")

                logger?.report(
                    CompilerMessageSeverity.INFO,
                    "Adding transitionTo from state [$currentState] on event [$currentEvent] to state [$target]"
                )
                graph.edges.add(
                    Edge(
                        from = currentState ?: "UNKNOWN",
                        to = name,
                        event = currentEvent ?: "UNKNOWN"
                    )
                )
                currentEvent = "UNKNOWN"
            }
            "transitionWith" -> {
                val target = expression.typeArguments.firstOrNull()?.classHierarchyName()
                    ?: "UnknownTarget"

                logger?.report(
                    CompilerMessageSeverity.INFO,
                    "Adding transitionWith from state [$currentState] on event [$currentEvent] to state [$target]"
                )
                graph.edges.add(
                    Edge(
                        from = currentState ?: "UNKNOWN",
                        to = target,
                        event = currentEvent ?: "UNKNOWN"
                    )
                )
                currentEvent = "UNKNOWN"
            }
        }

        super.visitCall(expression)
    }
}

/**
 * Step 5:
 * Write the mermaid file
 */
object MermaidWriter {

    fun toMermaid(graph: Graph): String {
        val sb = StringBuilder()
        sb.appendLine("graph TD") // Mermaid top-down graph

        // Declare states
        for (state in graph.states) {
            sb.appendLine("    $state[\"$state\"]")
        }

        // Declare edges
        for (edge in graph.edges) {
            sb.appendLine("    ${edge.from} --> ${edge.event}: ${edge.to}")
        }

        return sb.toString()
    }
}

/**
 * Helper to get class name including hierarchy (e.g. AdventureState.Start) but excluding package.
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
    return names.joinToString(".")
}