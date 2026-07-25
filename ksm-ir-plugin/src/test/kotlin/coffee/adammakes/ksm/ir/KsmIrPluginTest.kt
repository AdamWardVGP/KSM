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

        private const val compositeSource = """
            package coffee.adammakes.ksm.test

            import coffee.adammakes.ksm.stateMachine
            import kotlinx.coroutines.GlobalScope

            sealed class AppState {
                object Idle : AppState()
                object UpdateFlow : AppState()
                object Done : AppState()
            }

            sealed class AppEvent {
                object StartUpdate : AppEvent()
                object UpdateFinished : AppEvent()
            }

            sealed class UpdateState {
                object Checking : UpdateState()
                object Finished : UpdateState()
            }

            sealed class UpdateEvent {
                object Go : UpdateEvent()
            }

            fun buildChild() = stateMachine<UpdateState, UpdateEvent> {
                initialState = UpdateState.Checking
                dispatchedOn = GlobalScope

                state<UpdateState.Checking> {
                    on<UpdateEvent.Go>() transitionTo UpdateState.Finished
                }
            }

            val fsm = stateMachine<AppState, AppEvent> {
                initialState = AppState.Idle
                dispatchedOn = GlobalScope

                state<AppState.Idle> {
                    on<AppEvent.StartUpdate>() transitionTo AppState.UpdateFlow
                }
                state<AppState.UpdateFlow> {
                    on<AppEvent.UpdateFinished>() transitionTo AppState.Done
                    child(factory = { buildChild() }) {
                        exit<UpdateState.Finished> { AppEvent.UpdateFinished }
                    }
                }
                state<AppState.Done> {}
            }
        """

        private const val compositeMultiSource = """
            package coffee.adammakes.ksm.test

            import coffee.adammakes.ksm.stateMachine
            import kotlinx.coroutines.GlobalScope

            sealed class UpdateState {
                object Checking : UpdateState()
                object Finished : UpdateState()
            }

            sealed class UpdateEvent {
                object Go : UpdateEvent()
            }

            fun buildChild() = stateMachine<UpdateState, UpdateEvent> {
                initialState = UpdateState.Checking
                dispatchedOn = GlobalScope

                state<UpdateState.Checking> {
                    on<UpdateEvent.Go>() transitionTo UpdateState.Finished
                }
            }

            sealed class AppState {
                object Idle : AppState()
                object UpdateFlow : AppState()
                object Done : AppState()
            }

            sealed class AppEvent {
                object StartUpdate : AppEvent()
                object AppUpdateFinished : AppEvent()
            }

            val appFsm = stateMachine<AppState, AppEvent> {
                initialState = AppState.Idle
                dispatchedOn = GlobalScope

                state<AppState.Idle> {
                    on<AppEvent.StartUpdate>() transitionTo AppState.UpdateFlow
                }
                state<AppState.UpdateFlow> {
                    on<AppEvent.AppUpdateFinished>() transitionTo AppState.Done
                    child(factory = { buildChild() }) {
                        exit<UpdateState.Finished> { AppEvent.AppUpdateFinished }
                    }
                }
                state<AppState.Done> {}
            }

            sealed class SettingsState {
                object Idle : SettingsState()
                object UpdateFlow : SettingsState()
                object Done : SettingsState()
            }

            sealed class SettingsEvent {
                object StartUpdate : SettingsEvent()
                object SettingsUpdateFinished : SettingsEvent()
            }

            val settingsFsm = stateMachine<SettingsState, SettingsEvent> {
                initialState = SettingsState.Idle
                dispatchedOn = GlobalScope

                state<SettingsState.Idle> {
                    on<SettingsEvent.StartUpdate>() transitionTo SettingsState.UpdateFlow
                }
                state<SettingsState.UpdateFlow> {
                    on<SettingsEvent.SettingsUpdateFinished>() transitionTo SettingsState.Done
                    child(factory = { buildChild() }) {
                        exit<UpdateState.Finished> { SettingsEvent.SettingsUpdateFinished }
                    }
                }
                state<SettingsState.Done> {}
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

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `composite state produces one file per FSM, parent file rendering the child and exit wiring`() {
        val outputDir = outputDir("composite")
        try {
            val kotlinSource = SourceFile.kotlin("CompositeStateMachine.kt", compositeSource.trimIndent())
            val compilation = compilation("composite").apply {
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
            assertEquals(
                listOf("stateMachine_AppState.mmd", "stateMachine_UpdateState.mmd"),
                mmdFiles.map { it.name }.sorted(),
                "Expected exactly one file per FSM definition",
            )

            val parentContent = mmdFiles.first { it.name == "stateMachine_AppState.mmd" }.readText()
            assertTrue(
                !parentContent.contains("UpdateFlow --> Done: UpdateFinished"),
                "Exit-wired transition must not be duplicated on the main graph, got:\n$parentContent",
            )
            assertTrue(
                !parentContent.contains("state UpdateFlow {"),
                "Composite owner must stay a plain, unexpanded node — got:\n$parentContent",
            )
            assertTrue(
                parentContent.contains("state \"UpdateFlow\" as child_box_") &&
                    parentContent.contains("Checking") &&
                    parentContent.contains("Finished"),
                "Expected the child's expanded states, boxed under the owner state's name, got:\n$parentContent",
            )
            assertTrue(
                parentContent.contains("state \"Exit targets\" as exit_box_"),
                "Expected the exit-wiring mirror box, got:\n$parentContent",
            )
            assertTrue(
                parentContent.lines().count { it.contains(": UpdateFinished") } == 1,
                "Expected the exit-wiring edge to appear exactly once, got:\n$parentContent",
            )

            val childContent = mmdFiles.first { it.name == "stateMachine_UpdateState.mmd" }.readText()
            assertTrue(
                childContent.contains("Checking --> Finished: Go"),
                "Expected the child's own transition, got:\n$childContent",
            )
            assertTrue(
                !childContent.contains("AppState") && !childContent.contains("UpdateFlow"),
                "Child's standalone file must stay parent-agnostic, got:\n$childContent",
            )
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `same child FSM embedded at two sites produces one child file and two distinctly-wired parent files`() {
        val outputDir = outputDir("compositeMulti")
        try {
            val kotlinSource =
                SourceFile.kotlin("CompositeMultiStateMachine.kt", compositeMultiSource.trimIndent())
            val compilation = compilation("compositeMulti").apply {
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
            assertEquals(
                listOf(
                    "stateMachine_AppState.mmd",
                    "stateMachine_SettingsState.mmd",
                    "stateMachine_UpdateState.mmd",
                ),
                mmdFiles.map { it.name }.sorted(),
                "Expected exactly one child file shared by both embeddings, plus one file per parent",
            )

            val appContent = mmdFiles.first { it.name == "stateMachine_AppState.mmd" }.readText()
            val settingsContent = mmdFiles.first { it.name == "stateMachine_SettingsState.mmd" }.readText()

            assertTrue(
                appContent.contains(": AppUpdateFinished") &&
                    !appContent.contains("SettingsUpdateFinished"),
                "App's file must show its own exit wiring only, got:\n$appContent",
            )
            assertTrue(
                settingsContent.contains(": SettingsUpdateFinished") &&
                    !settingsContent.contains("AppUpdateFinished"),
                "Settings' file must show its own exit wiring only, got:\n$settingsContent",
            )

            // Both embed the same child FSM — each parent's file expands it, boxed under its own owner state name.
            assertTrue(appContent.contains("state \"UpdateFlow\" as child_box_"))
            assertTrue(settingsContent.contains("state \"UpdateFlow\" as child_box_"))

            val childContent = mmdFiles.first { it.name == "stateMachine_UpdateState.mmd" }.readText()
            assertTrue(
                childContent.contains("Checking --> Finished: Go"),
                "Expected the child's own transition, got:\n$childContent",
            )
            assertTrue(
                !childContent.contains("AppState") && !childContent.contains("SettingsState"),
                "Shared child's standalone file must stay parent-agnostic regardless of how many " +
                    "parents embed it, got:\n$childContent",
            )
        } finally {
            outputDir.deleteRecursively()
        }
    }
}
