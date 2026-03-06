package coffee.adammakes.ksm.ir

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class KsmCommandLineProcessor : CommandLineProcessor {
    override val pluginId = "coffee.adammakes.ksm.ir"
    override val pluginOptions = listOf(
        CliOption("outputDir", "<path>", "Directory for .mmd output files", required = false)
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        if (option.optionName == "outputDir") configuration.put(OUTPUT_DIR_KEY, value)
    }
}
