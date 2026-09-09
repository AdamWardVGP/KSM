package coffee.adammakes.ksm.ir

import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

private const val SUPPORTED_KOTLIN_MINOR = "2.2"

@OptIn(ExperimentalCompilerApi::class)
class KsmGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create("ksm", KsmExtension::class.java)
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        if (kotlinCompilation.target.platformType == KotlinPlatformType.native) return false

        val project = kotlinCompilation.target.project
        val kotlinVersion =
            project.extensions.findByType(KotlinProjectExtension::class.java)?.coreLibrariesVersion
                ?: return true // can't determine; apply optimistically

        val compatible = kotlinVersion.startsWith("$SUPPORTED_KOTLIN_MINOR.")
        if (!compatible) {
            project.logger.warn(
                "[KSM] ksm-ir-plugin was compiled against Kotlin $SUPPORTED_KOTLIN_MINOR.x " +
                    "but this project uses Kotlin $kotlinVersion. " +
                    "Glyphic diagram generation will be skipped. " +
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
        val defaultOutputDir = project.layout.buildDirectory.dir("ksmGraphs")
        return project.provider {
            val extension = project.extensions.getByType(KsmExtension::class.java)
            val outputDir = extension.outputDir.orElse(defaultOutputDir).get().asFile.absolutePath
            listOf(SubpluginOption("outputDir", outputDir))
        }
    }

    private fun loadVersion(): String =
        KsmGradlePlugin::class.java
            .getResourceAsStream("/version.properties")
            ?.use { Properties().apply { load(it) }.getProperty("version", "unknown") }
            ?: "unknown"
}
