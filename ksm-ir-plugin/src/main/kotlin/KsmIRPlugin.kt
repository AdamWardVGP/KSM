
import model.Edge
import model.Graph
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.fqNameForIrSerialization
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
/**
 * Step 1:
 * Register our plugin with the compiler.
 */
class KsmIrComponentRegistrar : CompilerPluginRegistrar() {

    override val pluginId: String
        get() = "ksm-ir-plugin"

    override val supportsK2: Boolean
        get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(
            extension = KsmIrGenerationExtension()
        )
    }
}

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

    private val outputDir: File by lazy {
        File(System.getProperty("user.dir"), "build/ksmGraphs").apply { mkdirs() }
    }

    override fun visitCall(expression: IrCall) {
        super.visitCall(expression)

        val callee = expression.symbol.owner
        val name = callee.name.asString()

        val owner = expression.symbol.owner
        if (
            owner.name.asString() == "stateMachine" &&
            owner.parent.fqNameForIrSerialization.asString() == "dev.adamwardvgp.ksm"
        ) {
            handleStateMachine(expression)
        }
    }


    private fun handleStateMachine(call: IrCall) {
        // stateMachine { ... }
        val lambda = call.arguments[0] as? IrFunctionExpression
            ?: return

        val graph = Graph("Adventure")

        lambda.function.body?.accept(
            StateMachineDslVisitor(graph),
            null
        )

        println(graph) // replace with Mermaid writer
        // Write to file instead of println
        val file = File(outputDir, "stateMachine_${graph.name}.mmd")
        file.writeText(MermaidWriter.toMermaid(graph)) // or simple graph.toString()

    }
}

/**
 * Step 4:
 * Traverse our DSL and extract entrance states, events, and target states.
 */
class StateMachineDslVisitor(
    private val graph: Graph
) : IrVisitorVoid() {

    private var currentState: String? = null
    private var currentEvent: String? = "UNKNOWN"

    override fun visitCall(expression: IrCall) {
        super.visitCall(expression)

        when (expression.symbol.owner.name.asString()) {

            "state" -> {
                val type = expression.arguments[0]
                currentState = type?.render()
                println("Adding state $currentState")
                graph.states.add(currentState!!)
            }

            "on" -> {
                currentEvent = expression.arguments[0]?.render() ?: "UNKNOWN"
            }

            "transitionTo" -> {
                val targetType = expression.arguments[0]
                val target = targetType?.render()

                println("Adding transition from state $currentState on event $currentEvent to state $target")
                graph.edges.add(
                    Edge(
                        from = currentState ?: "UNKNOWN",
                        to = target ?: "UNKNOWN",
                        event = currentEvent ?: "UNKNOWN"
                    )
                )
                currentEvent = "UNKNOWN"
            }
        }
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
            sb.appendLine("    ${edge.from} -->|${edge.event}| ${edge.to}")
        }

        return sb.toString()
    }
}

fun IrType.render(): String =
    when (val owner = (this as? IrSimpleType)?.classifier?.owner) {
        is IrClass -> owner.name.asString()
        is IrTypeParameter -> owner.name.asString()
        else -> "Unknown"
    }