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
        CliOption("outputDir", "<path>", "Directory for output files", required = false),
        CliOption("outputFormat", "<format>", "Output format (currently: mmd)", required = false),
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            "outputDir" -> configuration.put(OUTPUT_DIR_KEY, value)
            "outputFormat" -> configuration.put(OUTPUT_FORMAT_KEY, value)
        }
    }
}
