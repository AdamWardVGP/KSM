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

            val content = mmdFiles!!.first().readText()
            assertTrue(content.contains("stateDiagram-v2"), "Expected stateDiagram-v2 in output")
            assertTrue(content.contains("Initial --> Final: Move"), "Expected transition in output")
        } finally {
            outputDir.deleteRecursively()
        }
    }
}
