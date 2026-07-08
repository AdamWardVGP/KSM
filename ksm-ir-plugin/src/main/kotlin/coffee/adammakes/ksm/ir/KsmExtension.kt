package coffee.adammakes.ksm.ir

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

abstract class KsmExtension {
    abstract val outputDir: DirectoryProperty
    abstract val outputFormat: Property<String>
}
