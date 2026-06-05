package com.rsicarelli.kmptargets.source

import java.io.File
import java.util.Properties
import org.gradle.api.provider.ProviderFactory

/**
 * Returns a [SelectionSource] that delegates to each [sources] in order and yields the first
 * non-null result. An empty composition (no sources) reads as `null`.
 */
public fun composeSelectionSources(vararg sources: SelectionSource): SelectionSource =
    SelectionSource {
        sources.firstNotNullOfOrNull { it.read() }
    }

/**
 * Reads `kmptargets.targets` from a Gradle property (`-P<name>=...` or `gradle.properties`).
 *
 * The [providers] reference is captured at construction and the property value is fetched lazily on
 * each [read] call, so the Gradle configuration cache treats the property as a tracked input via
 * the normal `ProviderFactory` mechanism.
 */
public class GradlePropertySource(
    private val providers: ProviderFactory,
    private val name: String,
) : SelectionSource {

    override fun read(): String? = providers.gradleProperty(name).orNull
}

/**
 * Reads `kmptargets.targets` from an environment variable. Same configuration-cache contract as
 * [GradlePropertySource].
 */
public class EnvironmentVariableSource(
    private val providers: ProviderFactory,
    private val name: String,
) : SelectionSource {

    override fun read(): String? = providers.environmentVariable(name).orNull
}

/**
 * Reads `kmptargets.targets` from a `local.properties` file in [rootDir].
 *
 * This implementation reads the file directly; the plugin wraps it in a Gradle `ValueSource` so the
 * file is tracked as a configuration-cache input. Used directly (without that wrapper) only in
 * tests.
 */
public class LocalPropertiesSource(private val rootDir: File, private val propertyName: String) :
    SelectionSource {

    override fun read(): String? {
        val file = rootDir.resolve("local.properties")
        if (!file.exists()) return null
        val properties = Properties()
        file.inputStream().use(properties::load)
        return properties.getProperty(propertyName)
    }
}
