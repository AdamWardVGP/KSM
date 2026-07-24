package coffee.adammakes.ksm.ir


import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class KsmIrPluginTest {

    companion object {
        private const val testSource = """
            package coffee.adammakes.ksm.test

            import coffee.adammakes.ksm.stateMachine
            import kotlinx.coroutines.GlobalScope

            sealed class TestState {
                object Initial : TestState()
                object Final : TestState()
            }

            sealed class TestEvent {
                object Move : TestEvent()
            }

            val fsm = stateMachine<TestState, TestEvent> {
                initialState = TestState.Initial
                dispatchedOn = GlobalScope

                state<TestState.Initial> {
                    on<TestEvent.Move>() transitionTo TestState.Final
                }
            }
        """

        private const val nestedSource = """
            package coffee.adammakes.ksm.test

            sealed interface TestState
            open class Parent : TestState
            class Child : Parent()
            object Done : TestState

            sealed interface TestEvent {
                object ParentMove : TestEvent
                object ChildMove : TestEvent
                object Reset : TestEvent
            }

            class MachineBuilder<State : Any, Event : Any> {
                inline fun <reified S : State> state(
                    block: StateBuilder<S, State, Event>.() -> Unit,
                ) {
                    StateBuilder<S, State, Event>().block()
                }
            }

            class StateBuilder<From : State, State : Any, Event : Any> {
                inline fun <reified S : From> state(
                    block: StateBuilder<S, State, Event>.() -> Unit,
                ) {
                    StateBuilder<S, State, Event>().block()
                }

                inline fun <reified E : Event> on() = TransitionBuilder<E, State>()
            }

            class TransitionBuilder<Event, State : Any> {
                infix fun transitionTo(target: State) = Unit
            }

            inline fun <reified State : Any, reified Event : Any> stateMachine(
                block: MachineBuilder<State, Event>.() -> Unit,
            ) = MachineBuilder<State, Event>().block()

            val fsm = stateMachine<TestState, TestEvent> {
                state<Parent> {
                    on<TestEvent.ParentMove>() transitionTo Done
                    state<Child> {
                        on<TestEvent.ChildMove>() transitionTo Done
                    }
                    on<TestEvent.Reset>() transitionTo Parent()
                }
                state<Done> {}
            }
        """

        private fun outputDir(name: String): File =
            File("build/test-output/$name").absoluteFile.apply {
                deleteRecursively()
                mkdirs()
            }

        @OptIn(ExperimentalCompilerApi::class)
        private fun compilation(name: String): KotlinCompilation =
            KotlinCompilation().apply {
                workingDir = outputDir("compile-testing/$name")
                jvmTarget = "21"
                inheritClassPath = true
                messageOutputStream = System.out
            }
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `plugin registers and runs`() {
        val kotlinSource = SourceFile.kotlin(
            "TestStateMachine.kt", testSource.trimIndent()
        )

        val compilation = compilation("registers").apply {
            sources = listOf(kotlinSource)
            compilerPluginRegistrars = listOf(KsmIrComponentRegistrar())
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `plugin generates mermaid output file`() {
        val outputDir = outputDir("flat")
        try {
            val kotlinSource = SourceFile.kotlin(
                "TestStateMachine.kt", testSource.trimIndent()
            )

            val compilation = compilation("flat").apply {
                sources = listOf(kotlinSource)
                compilerPluginRegistrars = listOf(KsmIrComponentRegistrar())
                commandLineProcessors = listOf(KsmCommandLineProcessor())
                pluginOptions = listOf(
                    PluginOption("coffee.adammakes.ksm.ir", "outputDir", outputDir.absolutePath)
                )
            }

            val result = compilation.compile()
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

            val mmdFiles = outputDir.listFiles { _, name -> name.endsWith(".mmd") }
            assertTrue(
                mmdFiles != null && mmdFiles.isNotEmpty(),
                "Expected at least one .mmd file to be generated in $outputDir"
            )
            assertEquals(
                listOf("stateMachine_TestState.mmd"),
                mmdFiles.map { it.name }.sorted(),
            )

            val content = mmdFiles.first().readText()
            assertTrue(content.contains("stateDiagram-v2"), "Expected stateDiagram-v2 in output")
            assertTrue(content.contains("Initial --> Final: Move"), "Expected transition in output")
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `nested DSL renders hierarchy independently of Kotlin lexical nesting`() {
        val outputDir = outputDir("nested")
        try {
            val kotlinSource = SourceFile.kotlin("NestedStateMachine.kt", nestedSource.trimIndent())
            val compilation = compilation("nested").apply {
                sources = listOf(kotlinSource)
                compilerPluginRegistrars = listOf(KsmIrComponentRegistrar())
                commandLineProcessors = listOf(KsmCommandLineProcessor())
                pluginOptions = listOf(
                    PluginOption("coffee.adammakes.ksm.ir", "outputDir", outputDir.absolutePath)
                )
            }

            val result = compilation.compile()
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

            val mmdFiles = outputDir.listFiles { _, name -> name.endsWith(".mmd") }
            assertTrue(mmdFiles != null && mmdFiles.isNotEmpty())

            val content = mmdFiles.first().readText()
            assertTrue(
                content.contains(
                    "    state Parent {\n" +
                        "        Child\n" +
                        "    }"
                ),
                "Expected compound parent and child states, got:\n$content",
            )
            assertTrue(
                content.contains("Parent --> Done: ParentMove"),
                "Expected parent transition, got:\n$content",
            )
            assertTrue(
                content.contains("Child --> Done: ChildMove"),
                "Expected child transition, got:\n$content",
            )
            assertTrue(
                content.contains("Parent --> Parent: Reset"),
                "Expected parent attribution after nested declaration, got:\n$content",
            )
        } finally {
            outputDir.deleteRecursively()
        }
    }
}
