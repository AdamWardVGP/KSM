package dev.adamwardvgp.ksm.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
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

    override val pluginId: String
        get() = "ksm-ir-plugin"

    override val supportsK2: Boolean
        get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        logger = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

        logger?.report(CompilerMessageSeverity.INFO, "!!!!!Registering KSM IR plugin!!!!!")
        IrGenerationExtension.registerExtension(
            extension = KsmIrGenerationExtension()
        )
    }
}