package com.rsicarelli.kmptargets.source

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Gradle [ValueSource] wrapper around [LocalPropertiesSource] so the plugin's read of
 * `local.properties` is tracked as a configuration-cache input. Direct file reads at configuration
 * time would otherwise bypass the cache fingerprint.
 *
 * Used internally by the plugin; tests exercise the underlying [LocalPropertiesSource] directly.
 */
internal abstract class LocalPropertyValueSource :
    ValueSource<String, LocalPropertyValueSource.Params> {

    interface Params : ValueSourceParameters {
        val rootDir: DirectoryProperty
        val propertyName: Property<String>
    }

    override fun obtain(): String? =
        LocalPropertiesSource(
                rootDir = parameters.rootDir.asFile.get(),
                propertyName = parameters.propertyName.get(),
            )
            .read()
}
