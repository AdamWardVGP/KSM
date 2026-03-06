package coffee.adammakes.ksm.ir

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@OptIn(ExperimentalCompilerApi::class)
class KsmGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {}

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>) = true

    override fun getCompilerPluginId() = "coffee.adammakes.ksm.ir"

    override fun getPluginArtifact() = SubpluginArtifact(
        groupId = "coffee.adammakes.ksm",
        artifactId = "ksm-ir-plugin",
        version = "0.1.0"
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val outputDir = project.layout.buildDirectory.dir("ksmGraphs").get().asFile.absolutePath
        return project.provider { listOf(SubpluginOption("outputDir", outputDir)) }
    }
}
