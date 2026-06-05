package com.rsicarelli.kmptargets.source

import java.io.File
import java.util.Properties
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Reads a dedicated kmp-targets config file ([ConfigKeys.COMMITTED_FILE] or the personal
 * [ConfigKeys.LOCAL_FILE]) in [rootDir] as a whole key→value map.
 *
 * Returns `null` when the file is absent — the whole layer defers. When the file exists, a key
 * missing from the map means **absent** (defer to the next layer) while a blank value means
 * **present but blank** ("not overriding"), preserving the [SelectionSource] contract per key.
 * Reading the whole file (rather than one key) is what lets the plugin validate that every key is
 * known and fail loud on typos.
 *
 * This implementation reads the file directly; the plugin wraps it in [ConfigFileValueSource] so
 * the file is tracked as a configuration-cache input. Used directly (without that wrapper) only in
 * tests.
 */
public class ConfigPropertiesSource(private val rootDir: File, private val fileName: String) {

    public fun read(): Map<String, String>? {
        val file = rootDir.resolve(fileName)
        if (!file.exists()) return null
        val properties = Properties()
        file.inputStream().use(properties::load)
        return properties.entries.associate { (key, value) -> key.toString() to value.toString() }
    }
}

/**
 * Gradle [ValueSource] wrapper around [ConfigPropertiesSource] so the plugin's read of a dedicated
 * config file is tracked as a configuration-cache input: unchanged content lets the cache be
 * reused, an edit invalidates it. Mirrors [LocalPropertyValueSource].
 */
internal abstract class ConfigFileValueSource :
    ValueSource<Map<String, String>, ConfigFileValueSource.Params> {

    interface Params : ValueSourceParameters {
        val rootDir: DirectoryProperty
        val fileName: Property<String>
    }

    override fun obtain(): Map<String, String>? =
        ConfigPropertiesSource(
                rootDir = parameters.rootDir.asFile.get(),
                fileName = parameters.fileName.get(),
            )
            .read()
}
