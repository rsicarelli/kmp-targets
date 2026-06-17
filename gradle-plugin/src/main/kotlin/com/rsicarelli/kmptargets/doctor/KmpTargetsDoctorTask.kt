package com.rsicarelli.kmptargets.doctor

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Triages what the plugin decided for this project and why — the *explanation* surface to
 * `kmpTargetsInfo`'s neutral *state dump*. It renders one `[!]` finding per active advisory
 * (inert #71, JVM-less #72, android-without-AGP #51, host-impossible #32, deprecated #43,
 * empty-overlap #10), plus the doctor-only `single-target KSP` finding (#73, no advisory), a clean
 * bill when healthy, and a project-edge **closure** section (#80).
 *
 * Doctor *renders*, it does not *own* predicates: every single-module property below is wired from
 * the **same** provider/decision function the advisory and `kmpTargetsInfo` use, so it can never
 * re-detect or drift. All are plain primitives wired post-`supports { }`; the action captures no
 * `Project`, extension, or model type. [dependencyData] is the only cross-project input — the
 * doctor-data **files** of this module's `project(...)` dependencies, resolved through a lenient
 * ArtifactView (so external and plugin-less deps simply vanish — closure limit #1).
 */
@DisableCachingByDefault(because = "console report with no outputs; it must always run")
public abstract class KmpTargetsDoctorTask : DefaultTask() {

    /** The path of the project being diagnosed, e.g. `:feature-mobile`. */
    @get:Internal public abstract val projectPath: Property<String>

    /** Whether this module ever called `supports { … }`. */
    @get:Internal public abstract val supportsDeclared: Property<Boolean>

    /** Sorted leaf ids of the resolved selection. */
    @get:Internal public abstract val selectionIds: ListProperty<String>

    /** Sorted leaf ids of the resolved supported set. */
    @get:Internal public abstract val supportedIds: ListProperty<String>

    /** Sorted leaf ids this module actually registered — also its closure "needs". */
    @get:Internal public abstract val registeredIds: ListProperty<String>

    /** Same decision as the empty-overlap advisory (#10). */
    @get:Internal public abstract val emptyOverlap: Property<Boolean>

    /** Same decision as the inert-module advisory (#71). */
    @get:Internal public abstract val inertModule: Property<Boolean>

    /** Same decision as the native-only-metadata advisory (#72). */
    @get:Internal public abstract val nativeOnlyMetadata: Property<Boolean>

    /** Same decision as the android-without-AGP advisory (#51). */
    @get:Internal public abstract val androidWithoutAgp: Property<Boolean>

    /** Sorted ids of registered leaves the current host cannot compile (#32); empty without KGP. */
    @get:Internal public abstract val hostImpossibleIds: ListProperty<String>

    /** The current host's display name; empty without KGP. */
    @get:Internal public abstract val hostLabel: Property<String>

    /** Sorted ids of registered leaves Kotlin marks deprecated (#43). */
    @get:Internal public abstract val registeredDeprecatedIds: ListProperty<String>

    /**
     * A KSP plugin is applied and exactly one target is registered, so the shared commonMain
     * metadata route (`kspCommonMainKotlinMetadata`) is absent. Doctor-only — there is deliberately
     * no build-time advisory, since whether the module generates into `commonMain` is unobservable.
     */
    @get:Internal public abstract val singleTargetKsp: Property<Boolean>

    /** Custom Gradle name the jvm leaf registered under (#49), or blank/unset for the default. */
    @get:Internal public abstract val jvmRegisteredAs: Property<String>

    /**
     * Whether this module declared an `appleFramework` (#107). The four facts below render the same
     * values `kmpTargetsInfo` shows, from the same providers, so the surfaces can never drift. They
     * render even when `supports { }` was never declared — a declared-but-unattached framework is
     * still reported (`attached: (none)`), distinct from the no-advisory state.
     */
    @get:Internal public abstract val frameworkDeclared: Property<Boolean>

    /** The declared framework's baseName (#107); blank when no framework was declared. */
    @get:Internal public abstract val frameworkBaseName: Property<String>

    /** Sorted ids of the Apple leaves the framework actually attached to (`registered ∩ on`). */
    @get:Internal public abstract val frameworkAttachedIds: ListProperty<String>

    /** The framework's creation-time native build types (e.g. `DEBUG`, `RELEASE`), sorted. */
    @get:Internal public abstract val frameworkBuildTypes: ListProperty<String>

    /** Whether the framework opted into XCFramework assembly (#106). */
    @get:Internal public abstract val frameworkXcframework: Property<Boolean>

    /** Same decision as the framework-unattached advisory (#107) — drives the `[!]` finding. */
    @get:Internal public abstract val frameworkUnattached: Property<Boolean>

    /**
     * The doctor-data files of this module's `project(...)` dependencies, resolved at execution
     * time through the lenient ArtifactView. Declaring them as inputs wires the dependency emit
     * tasks, so a `kmpTargetsDoctor` run materializes its dependencies' data first.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val dependencyData: ConfigurableFileCollection

    @TaskAction
    public fun report() {
        val dependencies =
            dependencyData.files.map { parseDoctorData(it.readText()) }.sortedBy { it.path }
        val gaps = computeClosureGaps(registeredIds.get(), dependencies)
        println(
            formatKmpTargetsDoctor(
                projectPath = projectPath.get(),
                supportsDeclared = supportsDeclared.get(),
                selectionIds = selectionIds.get(),
                supportedIds = supportedIds.get(),
                registeredIds = registeredIds.get(),
                emptyOverlap = emptyOverlap.get(),
                inertModule = inertModule.get(),
                nativeOnlyMetadata = nativeOnlyMetadata.get(),
                androidWithoutAgp = androidWithoutAgp.get(),
                hostImpossibleIds = hostImpossibleIds.get(),
                hostLabel = hostLabel.get(),
                registeredDeprecatedIds = registeredDeprecatedIds.get(),
                singleTargetKsp = singleTargetKsp.get(),
                jvmRegisteredAs = jvmRegisteredAs.orNull?.takeIf { it.isNotBlank() },
                frameworkDeclared = frameworkDeclared.get(),
                frameworkBaseName = frameworkBaseName.get(),
                frameworkAttachedIds = frameworkAttachedIds.get(),
                frameworkBuildTypes = frameworkBuildTypes.get(),
                frameworkXcframework = frameworkXcframework.get(),
                frameworkUnattached = frameworkUnattached.get(),
                closureDepCount = dependencies.size,
                closureGaps = gaps,
            )
        )
    }
}
