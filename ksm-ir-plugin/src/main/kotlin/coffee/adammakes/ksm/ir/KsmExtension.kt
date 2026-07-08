package coffee.adammakes.ksm.ir

import org.gradle.api.file.DirectoryProperty

abstract class KsmExtension {
    abstract val outputDir: DirectoryProperty
}
