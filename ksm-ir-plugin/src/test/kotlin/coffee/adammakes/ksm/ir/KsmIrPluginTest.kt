package coffee.adammakes.ksm.ir


import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import java.nio.file.Files
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

            import coffee.adammakes.ksm.stateMachine
            import kotlinx.coroutines.GlobalScope

            sealed class TestState {
                object Idle : TestState()
                sealed class Active : TestState() {
                    object Running : Active()
                    object Paused : Active()
                }
            }

            sealed class TestEvent {
                object Start : TestEvent()
                object Pause : TestEvent()
            }

            val fsm = stateMachine<TestState, TestEvent> {
                initialState = TestState.Idle
                dispatchedOn = GlobalScope

                state<TestState.Idle> {
                    on<TestEvent.Start>() transitionTo TestState.Active.Running
                }

                state<TestState.Active.Running> {
                    on<TestEvent.Pause>() transitionTo TestState.Active.Paused
                }
            }
        """
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `plugin registers and runs`() {
        val kotlinSource = SourceFile.kotlin(
            "TestStateMachine.kt", testSource.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(kotlinSource)
            compilerPluginRegistrars = listOf(KsmIrComponentRegistrar())
            jvmTarget = "21"
            inheritClassPath = true
            messageOutputStream = System.out
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `plugin generates mermaid output file`() {
        val outputDir = Files.createTempDirectory("ksm-ir-test").toFile()
        try {
            val kotlinSource = SourceFile.kotlin(
                "TestStateMachine.kt", testSource.trimIndent()
            )

            val compilation = KotlinCompilation().apply {
                sources = listOf(kotlinSource)
                compilerPluginRegistrars = listOf(KsmIrComponentRegistrar())
                commandLineProcessors = listOf(KsmCommandLineProcessor())
                pluginOptions = listOf(
                    PluginOption("coffee.adammakes.ksm.ir", "outputDir", outputDir.absolutePath)
                )
                jvmTarget = "21"
                inheritClassPath = true
                messageOutputStream = System.out
            }

            val result = compilation.compile()
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

            val mmdFiles = outputDir.listFiles { _, name -> name.endsWith(".mmd") }
            assertTrue(
                mmdFiles != null && mmdFiles.isNotEmpty(),
                "Expected at least one .mmd file to be generated in $outputDir"
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
    fun `nested sealed states render with dot separator`() {
        val outputDir = Files.createTempDirectory("ksm-nested-test").toFile()
        try {
            val kotlinSource = SourceFile.kotlin("NestedStateMachine.kt", nestedSource.trimIndent())
            val compilation = KotlinCompilation().apply {
                sources = listOf(kotlinSource)
                compilerPluginRegistrars = listOf(KsmIrComponentRegistrar())
                commandLineProcessors = listOf(KsmCommandLineProcessor())
                pluginOptions = listOf(
                    PluginOption("coffee.adammakes.ksm.ir", "outputDir", outputDir.absolutePath)
                )
                jvmTarget = "21"
                inheritClassPath = true
                messageOutputStream = System.out
            }

            val result = compilation.compile()
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

            val mmdFiles = outputDir.listFiles { _, name -> name.endsWith(".mmd") }
            assertTrue(mmdFiles != null && mmdFiles.isNotEmpty())

            val content = mmdFiles.first().readText()
            assertTrue(
                content.contains("Idle --> Active.Running: Start"),
                "Expected nested state with dot separator, got:\n$content",
            )
            assertTrue(
                content.contains("Active.Running --> Active.Paused: Pause"),
                "Expected nested-to-nested transition, got:\n$content",
            )
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `plugin respects outputFormat option`() {
        val outputDir = Files.createTempDirectory("ksm-format-test").toFile()
        try {
            val kotlinSource = SourceFile.kotlin("TestStateMachine.kt", testSource.trimIndent())
            val compilation = KotlinCompilation().apply {
                sources = listOf(kotlinSource)
                compilerPluginRegistrars = listOf(KsmIrComponentRegistrar())
                commandLineProcessors = listOf(KsmCommandLineProcessor())
                pluginOptions = listOf(
                    PluginOption("coffee.adammakes.ksm.ir", "outputDir", outputDir.absolutePath),
                    PluginOption("coffee.adammakes.ksm.ir", "outputFormat", "mmd"),
                )
                jvmTarget = "21"
                inheritClassPath = true
                messageOutputStream = System.out
            }

            val result = compilation.compile()
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

            val outputFiles = outputDir.listFiles { _, name -> name.endsWith(".mmd") }
            assertTrue(
                outputFiles != null && outputFiles.isNotEmpty(),
                "Expected .mmd files for outputFormat=mmd",
            )
        } finally {
            outputDir.deleteRecursively()
        }
    }
}
