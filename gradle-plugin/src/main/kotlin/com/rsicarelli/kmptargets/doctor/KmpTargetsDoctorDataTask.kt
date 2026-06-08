package com.rsicarelli.kmptargets.doctor

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Emits this project's *own* doctor-data — its path and the leaf ids it actually registered — to a
 * single flat `key=value` file (#80). It is the one thing that crosses a project boundary for the
 * cross-module closure check: a dependent module's `kmpTargetsDoctor` consumes this **file**
 * through a resolvable configuration, never a peer `Project` model, which is what keeps the whole
 * feature configuration-cache- and Isolated-Projects-safe.
 *
 * Both inputs are plain primitives wired by the plugin from the same providers `kmpTargetsInfo`
 * uses, so the file always reflects the final post-`supports { }` state and serializes cleanly into
 * the configuration cache; no `Project`, extension, or model type is ever captured.
 */
@DisableCachingByDefault(because = "emits a tiny primitive data file from already-cached inputs")
public abstract class KmpTargetsDoctorDataTask : DefaultTask() {

    /** The path of the project this data describes, e.g. `:core`. */
    @get:Input public abstract val projectPath: Property<String>

    /** Sorted leaf ids this project actually registered (`registered()`). */
    @get:Input public abstract val registeredIds: ListProperty<String>

    /** The file the dependent modules' doctor reads back. */
    @get:OutputFile public abstract val outputFile: RegularFileProperty

    @TaskAction
    public fun emit() {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(renderDoctorData(DoctorData(projectPath.get(), registeredIds.get())))
    }
}
