package coffee.adammakes.ksm.ir

import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

private const val SUPPORTED_KOTLIN_MINOR = "2.3"

@OptIn(ExperimentalCompilerApi::class)
class KsmGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {}

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val project = kotlinCompilation.target.project
        val kotlinVersion =
            project.extensions.findByType(KotlinProjectExtension::class.java)?.coreLibrariesVersion
                ?: return true // can't determine; apply optimistically

        val compatible = kotlinVersion.startsWith("$SUPPORTED_KOTLIN_MINOR.")
        if (!compatible) {
            project.logger.warn(
                "[KSM] ksm-ir-plugin was compiled against Kotlin $SUPPORTED_KOTLIN_MINOR.x " +
                    "but this project uses Kotlin $kotlinVersion. " +
                    "Mermaid diagram generation will be skipped. " +
                    "See https://github.com/AdamWardVGP/KSM for a compatible plugin version."
            )
        }
        return compatible
    }

    override fun getCompilerPluginId() = "coffee.adammakes.ksm.ir"

    override fun getPluginArtifact() =
        SubpluginArtifact(
            groupId = "coffee.adammakes.ksm",
            artifactId = "ksm-ir-plugin",
            version = loadVersion(),
        )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val outputDir = project.layout.buildDirectory.dir("ksmGraphs").get().asFile.absolutePath
        return project.provider { listOf(SubpluginOption("outputDir", outputDir)) }
    }

    private fun loadVersion(): String =
        KsmGradlePlugin::class.java
            .getResourceAsStream("/version.properties")
            ?.use { Properties().apply { load(it) }.getProperty("version", "unknown") }
            ?: "unknown"
}
