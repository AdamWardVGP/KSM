package coffee.adammakes.ksm.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration

var logger: MessageCollector? = null

@OptIn(ExperimentalCompilerApi::class)
/**
 * Step 1:
 * Register our plugin with the compiler.
 */
class KsmIrComponentRegistrar : CompilerPluginRegistrar() {

    override val supportsK2: Boolean
        get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        logger = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

        val outputDir = configuration.get(OUTPUT_DIR_KEY)
        IrGenerationExtension.registerExtension(extension = KsmIrGenerationExtension(outputDir))
    }
}
