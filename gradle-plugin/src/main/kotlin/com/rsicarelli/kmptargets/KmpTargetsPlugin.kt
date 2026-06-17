package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.doctor.KmpTargetsDoctorDataTask
import com.rsicarelli.kmptargets.doctor.KmpTargetsDoctorTask
import com.rsicarelli.kmptargets.hierarchy.computeHierarchySpec
import com.rsicarelli.kmptargets.hierarchy.resolveHierarchyCollapseEnabled
import com.rsicarelli.kmptargets.hierarchy.resolveHierarchyTemplateEnabled
import com.rsicarelli.kmptargets.hierarchy.toTemplate
import com.rsicarelli.kmptargets.host.currentHostEnabled
import com.rsicarelli.kmptargets.host.currentHostLabel
import com.rsicarelli.kmptargets.host.enforceHostCompatibility
import com.rsicarelli.kmptargets.host.hostImpossibleIds
import com.rsicarelli.kmptargets.info.KmpTargetsInfoTask
import com.rsicarelli.kmptargets.info.OriginLabels
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.parser.ParseResult
import com.rsicarelli.kmptargets.parser.didYouMean
import com.rsicarelli.kmptargets.parser.parseKmpTargets
import com.rsicarelli.kmptargets.parser.presetNames
import com.rsicarelli.kmptargets.source.ConfigFileValueSource
import com.rsicarelli.kmptargets.source.ConfigKeys
import com.rsicarelli.kmptargets.source.EnvironmentVariableSource
import com.rsicarelli.kmptargets.source.GradlePropertySource
import com.rsicarelli.kmptargets.source.LocalPropertyValueSource
import com.rsicarelli.kmptargets.source.SelectionSource
import com.rsicarelli.kmptargets.source.composeSelectionSources
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests
import org.jetbrains.kotlin.konan.target.HostManager

public class KmpTargetsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val ext = target.extensions.create("kmpTargets", KmpTargetsExtension::class.java)
        ext.defaultSelection.convention(KmpTargetSet.all)

        // Dedicated config files, each read once as a whole map (tracked config-cache inputs) and
        // validated against the key registry — a typo fails the build instead of silently no-oping
        // (Bazel-style, issue #30).
        val personal: Map<String, String>? = configFile(target, ConfigKeys.LOCAL_FILE)
        val committed: Map<String, String>? = configFile(target, ConfigKeys.COMMITTED_FILE)
        validateKnownKeys(personal, ConfigKeys.LOCAL_FILE)
        validateKnownKeys(committed, ConfigKeys.COMMITTED_FILE)

        // Global selection: what the user wants to build now. A blank/absent property means "not
        // overriding" → defer to `defaultSelection`. A non-blank property that
        // resolves to an empty set via minus operators (e.g. `jvm,-jvm`) is an explicit "build
        // nothing" and must be honored, not silently treated as the default-all selection (issue
        // #9). Keying off `isNotBlank` (rather than the parsed set's emptiness) is what
        // distinguishes the two cases. The first-non-null walk preserves the exact
        // `composeSelectionSources` semantics (lower layers stay unread once one wins) while also
        // capturing WHICH layer won, for `kmpTargetsInfo`'s origin report (issue #33).
        val winner: Pair<String, String>? =
            labeledSources(target, ConfigKeys.TARGETS, personal, committed).firstNotNullOfOrNull {
                (label, source) ->
                source.read()?.let { value -> label to value }
            }
        val raw: String? = winner?.second
        if (raw != null && raw.isNotBlank()) {
            ext.globalSelection = parseOrThrow(raw, ConfigKeys.TARGETS)
        }
        // A blank winner shadows lower layers but does not override the default, so it yields no
        // origin either — the report then falls through to the defaultSelection/built-in labels.
        val configOrigin: String? = winner?.takeIf { it.second.isNotBlank() }?.first

        // Global defaults for the hierarchy template and its collapse rule (issue #50), each read
        // once at apply time as a primitive.
        val globalHierarchyEnabled: Boolean? =
            hierarchyTemplateEnabledGlobally(target, personal, committed)
        val globalCollapseEnabled: Boolean? =
            hierarchyCollapseEnabledGlobally(target, personal, committed)

        // Strict mode (#34): opt-in promotion of the selection/host advisories to failures, read
        // once at apply time as a primitive Boolean (default off) so no `Project` is captured.
        val strict: Boolean = strictModeEnabled(target, personal, committed)

        // Eager registration: `supports { … }` in the build-script body registers `selection ∩
        // supported` immediately — no deferred (after-evaluate) pass, no timing wall. We hook it
        // via
        // `withPlugin(KGP)` so registration fires the moment both KGP and a `supports` declaration
        // are present (in either order); if KGP is never applied, nothing registers. This runs at
        // configuration time and is never held by a Task, so no `Project` leaks into the config
        // cache.
        ext.onSupports = {
            target.pluginManager.withPlugin(KGP_ID) {
                register(target, ext, globalHierarchyEnabled, globalCollapseEnabled, strict)
            }
        }

        // The framework-unattached advisory (#107) also fires on the appleFramework() replay path:
        // a
        // framework declared AFTER a (e.g. jvm-only) supports { } attaches nothing and no longer
        // re-runs register(), so this hook re-evaluates it under the same KGP guard and the same
        // apply-time strict primitive — never capturing a Project into a task.
        ext.onAppleFrameworkDeclared = {
            target.pluginManager.withPlugin(KGP_ID) {
                maybeWarnFrameworkUnattached(target, ext, strict)
            }
        }

        registerInfoTask(target, ext, configOrigin)
        registerDoctorTask(target, ext)

        // ABI × selection advisory (#81): when an ABI-validation task (kotlinx BCV or the built-in
        // abiValidation — detected by task name, so neither plugin is a dependency) runs under a
        // selection narrower than this module's supported set, it can only cover the registered
        // targets. The hook warns at that moment (strict → fails), so a narrowed
        // `apiDump`/`apiCheck`
        // never silently under-covers; a full-selection run leaves supported − registered empty and
        // stays silent.
        wireAbiNarrowingAdvisory(target, ext, strict)

        // Umbrella lifecycle tasks (#77): OPT-IN via `kmptargets.umbrellaTasks` (default off), read
        // once at apply time as a primitive. Off by default because the umbrellas add dependency
        // edges to every registered target's compile/test tasks — a build wants them only when it
        // drives CI off one stable name. When ON they are registered in EVERY module (the flag is
        // global), so an inert or never-supports module still carries a no-op task and an
        // unqualified
        // `./gradlew kmpCompileAll` from the root never 404s in any project. The eager register()
        // loop appends one dependency per registered leaf, so each ends up wired to exactly the
        // registered intersection; when OFF the carriers stay null and `wireUmbrellas` no-ops.
        if (umbrellaTasksEnabled(target, personal, committed)) {
            ext.compileAllTask =
                registerUmbrella(target, COMPILE_ALL_TASK, "build", COMPILE_ALL_DESC)
            ext.testAllTask = registerUmbrella(target, TEST_ALL_TASK, "verification", TEST_ALL_DESC)
        }
    }

    /**
     * Registers one umbrella lifecycle task — a pure aggregator with no `@TaskAction` and no
     * inputs; the eager [register] loop wires its dependencies. Registration is unconditional and
     * lazy: when the task is never requested it is never configured, and when a module registers
     * nothing it stays a clean no-op. Config-cache safe by construction — `dependsOn` added later
     * is configuration-time wiring, and the task captures no `Project` and serializes no state.
     */
    private fun registerUmbrella(
        target: Project,
        name: String,
        group: String,
        description: String,
    ): TaskProvider<Task> =
        target.tasks.register(name) { task ->
            task.group = group
            task.description = description
        }

    /**
     * Wires the umbrella tasks (#77) for one freshly registered [leaf]/[kotlinTarget], off the live
     * `KotlinTarget` so the dependency is the genuine task — rename-proof and impossible to
     * silently zero-match, the exact failure modes hardcoded CI task lists suffer.
     *
     * For every non-Android target KGP creates the `main` (and `test`) compilation synchronously
     * when the target is created, so the real **compile** task is wired via a provider
     * ([KotlinCompilation.compileTaskProvider]) — a genuine `TaskProvider` that can never silently
     * zero-match. The **test-run** task exists only for the [KotlinTargetWithTests] targets (a
     * device-only native such as `iosArm64` has none, and is correctly skipped); its public surface
     * does not expose the execution task, so it is wired by its stable KGP name `<targetName>Test`
     * — still rename-proof, because `targetName` is the real registered name (`desktopTest`, never
     * a hardcoded `jvmTest`). Android is the exception — its compilations/test tasks are
     * variant-named and created later by AGP — so its tasks are named by reconstruction from the
     * fixed `android` gradle name, matching the literals hardcoded CI already used.
     *
     * The umbrellas only ever depend on registered *platform* tasks; the commonMain metadata
     * compilation is never referenced, so they cannot re-introduce the inert (#71) or
     * JVM-less-fragment (#72) failures.
     */
    private fun wireUmbrellas(
        ext: KmpTargetsExtension,
        leaf: KmpTarget,
        kotlinTarget: KotlinTarget,
    ) {
        val compileAll = ext.compileAllTask
        val testAll = ext.testAllTask
        if (leaf == KmpTarget.Jvm.Android) {
            // AGP materializes both production variants for a library/application module; a literal
            // dependsOn(name) fails loudly if one is absent — loud beats the silent zero-match.
            val cap = kotlinTarget.targetName.replaceFirstChar(Char::titlecase)
            compileAll?.configure {
                it.dependsOn("compileDebugKotlin$cap", "compileReleaseKotlin$cap")
            }
            testAll?.configure { it.dependsOn("testDebugUnitTest", "testReleaseUnitTest") }
            return
        }
        compileAll?.configure {
            it.dependsOn(
                kotlinTarget.compilations.named(KotlinCompilation.MAIN_COMPILATION_NAME).flatMap {
                    compilation ->
                    compilation.compileTaskProvider
                }
            )
        }
        if (kotlinTarget is KotlinTargetWithTests<*, *>) {
            testAll?.configure { it.dependsOn("${kotlinTarget.targetName}Test") }
        }
    }

    /**
     * Registers the `kmpTargetsInfo` introspection task (issue #33): one task that answers "what
     * did the plugin decide for this module, and why?".
     *
     * Timing under the eager model: registration happens at apply time, but `supports { … }` only
     * unions the supported set while the build-script body runs. The selection/supported/registered
     * inputs are therefore wired as providers that map straight to sorted-id `String` lists —
     * Gradle realizes a scheduled task's property providers at task-graph calculation, after the
     * whole body ran, so they observe the final state and only primitives are serialized into the
     * configuration cache. Registration is lazy on purpose: when the task is not requested it is
     * never configured, so the providers never realize and nothing here can leak into the cache.
     *
     * The providers call only `resolvedSelection()`/`resolvedSupported()` — pure reads that never
     * fire `onSupports` — so the report can never register targets as a side effect. It is also
     * registered unconditionally (not gated on KGP): without KGP or `supports`, the report states
     * explicitly that nothing is declared and nothing registers.
     */
    private fun registerInfoTask(target: Project, ext: KmpTargetsExtension, configOrigin: String?) {
        val projectPath = target.path
        target.tasks.register("kmpTargetsInfo", KmpTargetsInfoTask::class.java) { task ->
            task.group = "help"
            task.description =
                "Prints the resolved kmp-targets selection, its origin, the supported set, and " +
                    "the registered intersection for this project."
            task.projectPath.set(projectPath)
            task.presetNames.set(presetNames)
            task.leafIds.set(KmpTarget.all.map { it.id }.sorted())
            task.deprecatedIds.set(KmpTarget.deprecated.map { it.id }.sorted())
            // Every diagnostic primitive comes from the shared builders below `registerDoctorTask`,
            // wired IDENTICALLY into this neutral state dump and the doctor triage report — same
            // decision functions (`shouldWarnInertModule` etc.), same KGP guards realized at
            // provider time (post-body). One source means the two surfaces can never drift, and
            // neither re-detects. The registered line here is the abstract `selection ∩ supported`;
            // doctor renders the concrete `registered()`.
            task.selectionIds.set(selectionIdsProvider(target, ext))
            task.supportedIds.set(supportedIdsProvider(target, ext))
            task.supportsDeclared.set(supportsDeclaredProvider(target, ext))
            task.registeredIds.set(intersectionIdsProvider(target, ext))
            task.hostImpossibleIds.set(hostImpossibleIdsProvider(target, ext))
            task.hostLabel.set(hostLabelProvider(target))
            task.jvmRegisteredAs.set(jvmRegisteredAsProvider(target, ext))
            task.androidWithoutAgp.set(androidWithoutAgpProvider(target, ext))
            task.inertModule.set(inertModuleProvider(target, ext))
            task.nativeOnlyMetadata.set(nativeOnlyMetadataProvider(target, ext))
            task.frameworkDeclared.set(frameworkDeclaredProvider(target, ext))
            task.frameworkBaseName.set(frameworkBaseNameProvider(target, ext))
            task.frameworkAttachedIds.set(frameworkAttachedIdsProvider(target, ext))
            task.frameworkBuildTypes.set(frameworkBuildTypesProvider(target, ext))
            task.frameworkXcframework.set(frameworkXcframeworkProvider(target, ext))
            // The config-layer origin is fixed at apply time, but the fallback labels must read
            // `defaultSelection` lazily — the body may override it after apply.
            task.originLabel.set(
                target.provider {
                    configOrigin
                        ?: if (ext.defaultSelection.get() == KmpTargetSet.all) {
                            OriginLabels.DEFAULT_BUILTIN
                        } else {
                            OriginLabels.DEFAULT_BUILD_LOGIC
                        }
                }
            )
        }
    }

    /**
     * Registers the doctor surfaces (#82): `kmpTargetsDoctor` — the per-project triage report that
     * *explains* why a module is inert or had targets disabled and flags project-edge closure gaps
     * — plus `kmpTargetsDoctorData`, the per-project emitter whose file dependent modules consume
     * for that closure check.
     *
     * Doctor renders, it does not own predicates: every single-module property is wired from the
     * same shared `…Provider` builders `kmpTargetsInfo` uses, so it can never re-detect or drift.
     *
     * The closure half (#80) is the only cross-module piece, and it crosses the boundary by FILE,
     * never by peer `Project` model — the IP-legal construction #80 specified. The producer emits
     * its own primitives and exposes them as an outgoing artifact on a dedicated consumable
     * configuration (a unique custom attribute keeps it clear of KGP's variants); the consumer
     * resolves the doctor-data of its own declared `project(...)` dependencies through a lenient
     * ArtifactView. Both permanent limits are honest and documented: external (non-`project(...)`)
     * dependencies are invisible, and the android→jvm fallback only approximates Gradle's attribute
     * matching.
     */
    private fun registerDoctorTask(target: Project, ext: KmpTargetsExtension) {
        val projectPath = target.path
        val dataFile = target.layout.buildDirectory.file("kmp-targets/doctor-data.properties")

        // Producer: emit this project's own (path, registered) primitives to a file and expose it
        // as
        // an outgoing artifact. The artifact's builtBy gives a dependent's doctor automatic
        // ordering
        // on this emit task — no cross-project task wiring at configuration time.
        val dataTask =
            target.tasks.register(DOCTOR_DATA_TASK, KmpTargetsDoctorDataTask::class.java) { task ->
                task.group = "help"
                task.description =
                    "Emits this project's kmp-targets doctor-data (path + registered leaves), " +
                        "consumed by dependent modules' kmpTargetsDoctor closure check."
                task.projectPath.set(projectPath)
                task.registeredIds.set(registeredIdsProvider(target, ext))
                task.outputFile.set(dataFile)
            }
        val elements =
            target.configurations.consumable(DOCTOR_DATA_ELEMENTS) {
                it.attributes.attribute(DOCTOR_DATA_ATTRIBUTE, DOCTOR_DATA_ATTRIBUTE_VALUE)
            }
        target.artifacts.add(elements.name, dataFile) { it.builtBy(dataTask) }

        // Consumer: a resolvable configuration extending a bucket we live-mirror this project's own
        // declared project(...) dependencies into (config-time, own-project coordinates only — no
        // afterEvaluate, no peer Project read). The lenient ArtifactView then resolves only the
        // deps
        // that expose the doctor-data variant; external and plugin-less deps vanish (limit #1).
        val closureDeps = target.configurations.dependencyScope(DOCTOR_CLOSURE_DEPS).get()
        val closure =
            target.configurations
                .resolvable(DOCTOR_CLOSURE) {
                    it.extendsFrom(closureDeps)
                    it.attributes.attribute(DOCTOR_DATA_ATTRIBUTE, DOCTOR_DATA_ATTRIBUTE_VALUE)
                }
                .get()
        mirrorProjectDependencies(target, closureDeps)
        val dependencyDataFiles = closure.incoming.artifactView { it.lenient(true) }.files

        target.tasks.register(DOCTOR_TASK, KmpTargetsDoctorTask::class.java) { task ->
            task.group = "help"
            task.description =
                "Explains why this module is inert or had targets disabled, and flags project-edge " +
                    "closure gaps — the kmp-targets diagnostic report."
            task.projectPath.set(projectPath)
            task.supportsDeclared.set(supportsDeclaredProvider(target, ext))
            task.selectionIds.set(selectionIdsProvider(target, ext))
            task.supportedIds.set(supportedIdsProvider(target, ext))
            task.registeredIds.set(registeredIdsProvider(target, ext))
            task.emptyOverlap.set(emptyOverlapProvider(target, ext))
            task.inertModule.set(inertModuleProvider(target, ext))
            task.nativeOnlyMetadata.set(nativeOnlyMetadataProvider(target, ext))
            task.androidWithoutAgp.set(androidWithoutAgpProvider(target, ext))
            task.hostImpossibleIds.set(hostImpossibleIdsProvider(target, ext))
            task.hostLabel.set(hostLabelProvider(target))
            task.registeredDeprecatedIds.set(registeredDeprecatedIdsProvider(target, ext))
            task.singleTargetKsp.set(singleTargetKspProvider(target, ext))
            task.jvmRegisteredAs.set(jvmRegisteredAsProvider(target, ext))
            task.frameworkDeclared.set(frameworkDeclaredProvider(target, ext))
            task.frameworkBaseName.set(frameworkBaseNameProvider(target, ext))
            task.frameworkAttachedIds.set(frameworkAttachedIdsProvider(target, ext))
            task.frameworkBuildTypes.set(frameworkBuildTypesProvider(target, ext))
            task.frameworkXcframework.set(frameworkXcframeworkProvider(target, ext))
            task.frameworkUnattached.set(frameworkUnattachedProvider(target, ext))
            task.dependencyData.from(dependencyDataFiles)
        }
    }

    /**
     * Hooks the ABI-validation lifecycle tasks (#81) so a run under a narrowed selection cannot
     * silently under-cover. Identification is by **task name** ([ABI_TASK_NAMES]) — the only way
     * that catches BOTH the kotlinx-BCV plugin and Kotlin's built-in `abiValidation` (which has no
     * separate plugin id) without the plugin taking a dependency on either. When none of those
     * tasks exists, [org.gradle.api.tasks.TaskCollection.configureEach] matches nothing and this is
     * a complete no-op.
     *
     * The gate is the diff the plugin already owns: `resolvedSupported() − registered()` — the
     * targets this module *can* build that the current selection did not register. An ABI tool only
     * ever sees the registered targets, so a non-empty diff means the run under-covers exactly that
     * set. The check is read inside `configureEach` (task realization, after the build-script
     * body), so the registration snapshot is final, and only the resulting `List<String>` (plus the
     * path and the strict flag) is captured into the `doFirst` action — never the extension or
     * `Project`, so the task stays configuration-cache safe. The warning fires at the moment the
     * ABI task runs; under strict mode (#34) it fails instead, blocking a narrowed
     * `apiDump`/`updateKotlinAbi` from committing an under-covered dump. A full-selection run
     * leaves the diff empty and stays silent.
     */
    private fun wireAbiNarrowingAdvisory(
        target: Project,
        ext: KmpTargetsExtension,
        strict: Boolean,
    ) {
        val path = target.path
        target.tasks.configureEach { task ->
            if (task.name !in ABI_TASK_NAMES) return@configureEach
            val uncovered = abiUncoveredGroups(ext.resolvedSupported(), ext.registered())
            if (uncovered.isEmpty()) return@configureEach
            val taskName = task.name
            task.doFirst { realized ->
                warnOrFail(strict, abiNarrowingWarning(path, taskName, uncovered)) {
                    realized.logger.warn(it)
                }
            }
        }
    }

    /**
     * Live-mirrors every `project(...)` dependency this project declares (now or later in the body)
     * into [closureDeps] as a plain coordinate, so the closure configuration resolves those
     * dependencies' doctor-data without an `afterEvaluate` and without reading any peer `Project`
     * model. Deduped by path; our own configurations are skipped so the mirror never feeds itself.
     */
    private fun mirrorProjectDependencies(target: Project, closureDeps: Configuration) {
        val seen = mutableSetOf<String>()
        target.configurations.all { configuration ->
            if (configuration.name.startsWith("kmpTargetsDoctor")) return@all
            configuration.dependencies.all { dependency ->
                if (dependency is ProjectDependency && seen.add(dependency.path)) {
                    closureDeps.dependencies.add(
                        target.dependencies.project(mapOf("path" to dependency.path))
                    )
                }
            }
        }
    }

    /**
     * Fires the framework-unattached advisory (#107) at most once per module: when a framework is
     * declared, this module declared `supports { }`, and zero Apple leaves in the framework's `on`
     * scope attached. Shared by the end of every [register] pass and the [appleFramework] replay
     * hook, so it fires regardless of whether the framework was declared before or after `supports
     * { }`. The dedup flag is set AFTER [warnOrFail] (mirroring the sibling advisories) so a strict
     * failure leaves no bookkeeping behind. Keyed off the `on`-scoped attached-leaf set, so a
     * framework scoped to leaves the selection never registered is flagged even when other Apple
     * leaves did register.
     */
    private fun maybeWarnFrameworkUnattached(
        project: Project,
        ext: KmpTargetsExtension,
        strict: Boolean,
    ) {
        if (
            !ext.frameworkUnattachedWarned &&
                shouldWarnFrameworkUnattached(
                    ext.appleFrameworkDeclared,
                    ext.supportsProperty.isPresent,
                    ext.frameworkAttachedLeaves(),
                )
        ) {
            warnOrFail(
                strict,
                frameworkUnattachedWarning(
                    project.path,
                    ext.appleFrameworkFacts?.baseName.orEmpty(),
                ),
            ) {
                project.logger.warn(it)
            }
            ext.frameworkUnattachedWarned = true
        }
    }

    // ---- Shared diagnostic provider builders (renders, not owns) --------------------------------
    // One source for each diagnostic primitive, wired identically into kmpTargetsInfo and
    // kmpTargetsDoctor. Each KGP-guarded provider calls the SAME decision function as its advisory,
    // realized at provider time (task-graph calc, post-body) so it observes the final state.

    private fun hasKgp(target: Project): Boolean = target.pluginManager.hasPlugin(KGP_ID)

    private fun selectionIdsProvider(target: Project, ext: KmpTargetsExtension) = target.provider {
        ids(ext.resolvedSelection())
    }

    private fun supportedIdsProvider(target: Project, ext: KmpTargetsExtension) = target.provider {
        ids(ext.resolvedSupported())
    }

    private fun supportsDeclaredProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            ext.supportsProperty.isPresent
        }

    /** The abstract `selection ∩ supported` (what `kmpTargetsInfo` reports). */
    private fun intersectionIdsProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            ids(ext.resolvedSelection() intersect ext.resolvedSupported())
        }

    /** The concrete `registered()` (what doctor reports and the closure check needs). */
    private fun registeredIdsProvider(target: Project, ext: KmpTargetsExtension) = target.provider {
        ids(ext.registered())
    }

    private fun hostImpossibleIdsProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            if (!hasKgp(target)) emptyList()
            else
                hostImpossibleIds(
                    ext.resolvedSelection() intersect ext.resolvedSupported(),
                    currentHostEnabled(),
                )
        }

    private fun hostLabelProvider(target: Project) = target.provider {
        if (!hasKgp(target)) "" else currentHostLabel()
    }

    private fun jvmRegisteredAsProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            ext.jvmTargetName.orNull
        }

    private fun androidWithoutAgpProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            hasKgp(target) &&
                shouldWarnAndroidWithoutAgp(
                    ext.resolvedSelection() intersect ext.resolvedSupported(),
                    isAndroidPluginApplied(target),
                )
        }

    private fun inertModuleProvider(target: Project, ext: KmpTargetsExtension) = target.provider {
        hasKgp(target) && shouldWarnInertModule(ext.supportsProperty.isPresent, ext.registered())
    }

    private fun nativeOnlyMetadataProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            hasKgp(target) &&
                shouldWarnNativeOnlyMetadata(ext.resolvedSupported(), ext.registered())
        }

    private fun emptyOverlapProvider(target: Project, ext: KmpTargetsExtension) = target.provider {
        hasKgp(target) && shouldWarnEmptyOverlap(ext.resolvedSelection(), ext.resolvedSupported())
    }

    private fun registeredDeprecatedIdsProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            ids(ext.registered() intersect deprecatedSet)
        }

    private fun singleTargetKspProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            hasKgp(target) &&
                shouldWarnSingleTargetKsp(target.pluginManager.hasPlugin(KSP_ID), ext.registered())
        }

    // Framework facts (#107), rendered identically by kmpTargetsInfo and kmpTargetsDoctor. The
    // declaration facts (declared / name / buildTypes / xcframework) are KGP-independent — a
    // framework is declared whether or not KGP applied — while the attached-leaf and unattached
    // providers reflect what actually registered (naturally empty/false without KGP).
    // frameworkUnattached calls the SAME predicate the advisory uses, so doctor's finding can never
    // drift from the warning.
    private fun frameworkDeclaredProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            ext.appleFrameworkDeclared
        }

    private fun frameworkBaseNameProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            ext.appleFrameworkFacts?.baseName.orEmpty()
        }

    private fun frameworkAttachedIdsProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            ids(ext.frameworkAttachedLeaves())
        }

    private fun frameworkBuildTypesProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            ext.appleFrameworkFacts?.buildTypes?.map { it.name }?.sorted().orEmpty()
        }

    private fun frameworkXcframeworkProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            ext.appleFrameworkFacts?.xcframework ?: false
        }

    private fun frameworkUnattachedProvider(target: Project, ext: KmpTargetsExtension) =
        target.provider {
            hasKgp(target) &&
                shouldWarnFrameworkUnattached(
                    ext.appleFrameworkDeclared,
                    ext.supportsProperty.isPresent,
                    ext.frameworkAttachedLeaves(),
                )
        }

    internal companion object {
        const val KGP_ID: String = "org.jetbrains.kotlin.multiplatform"

        /**
         * The KSP Gradle plugin id — its presence + a single registered target drives #73's
         * finding.
         */
        const val KSP_ID: String = "com.google.devtools.ksp"

        /**
         * The ABI-validation lifecycle task names the narrowing advisory (#81) hooks — the umbrella
         * tasks users run, for both ABI tools: `apiDump`/`apiCheck` (kotlinx-BCV) and
         * `updateKotlinAbi`/`checkKotlinAbi` (Kotlin's built-in `abiValidation`). Matching by name
         * is what lets the plugin cover the built-in tool, which has no separate plugin id, without
         * depending on either.
         */
        val ABI_TASK_NAMES: Set<String> =
            setOf("apiDump", "apiCheck", "updateKotlinAbi", "checkKotlinAbi")

        const val DOCTOR_TASK: String = "kmpTargetsDoctor"
        const val DOCTOR_DATA_TASK: String = "kmpTargetsDoctorData"
        const val DOCTOR_DATA_ELEMENTS: String = "kmpTargetsDoctorDataElements"
        const val DOCTOR_CLOSURE_DEPS: String = "kmpTargetsDoctorClosureDeps"
        const val DOCTOR_CLOSURE: String = "kmpTargetsDoctorClosure"

        /** Unique attribute the doctor-data variant carries, so it never collides with KGP's. */
        val DOCTOR_DATA_ATTRIBUTE: Attribute<String> =
            Attribute.of("com.rsicarelli.kmptargets.doctordata", String::class.java)

        const val DOCTOR_DATA_ATTRIBUTE_VALUE: String = "doctor-data"

        const val COMPILE_ALL_TASK: String = "kmpCompileAll"
        const val TEST_ALL_TASK: String = "kmpTestAll"

        const val COMPILE_ALL_DESC: String =
            "Compiles every target this module registered — the registered intersection, not a " +
                "hardcoded leaf list — so it stays correct under any selection and a renamed jvm " +
                "leaf. Excludes the commonMain metadata compilation, so it never re-introduces the " +
                "inert (#71) or JVM-less-fragment (#72) failures."

        const val TEST_ALL_DESC: String =
            "Runs the tests of every target this module registered that has a test task — " +
                "selection-agnostic and rename-proof, so a renamed jvm leaf's tests still run " +
                "where a hardcoded jvmTest would silently match nothing."
    }

    /**
     * The full precedence chain for one global config key (highest first): CLI `-P` →
     * `ORG_GRADLE_PROJECT_<key>` env → personal file → committed file → `gradle.properties` →
     * `local.properties` — each layer paired with the [OriginLabels] name `kmpTargetsInfo` reports
     * when that layer wins. Both global knobs read through this one chain (via [configSources]), so
     * they always share the same precedence, and origin reporting can never drift from value
     * resolution. Adding a future config layer means adding exactly one entry here.
     *
     * The top of the chain is decomposed by hand: `providers.gradleProperty` fuses CLI, env, and
     * `gradle.properties` into one provider with no origin information, but the dedicated files
     * must slot *between* env and `gradle.properties` (the consolidation point beats legacy loose
     * keys, issue #30). So CLI is read eagerly from the start parameter (a config-cache input — any
     * `-P` change invalidates the entry) and env explicitly via Gradle's native
     * `ORG_GRADLE_PROJECT_` mapping; `gradleProperty` then serves as the `gradle.properties` layer.
     * It still re-matches CLI/env values, but the first-non-null composition has already caught
     * those above, so the overlap is harmless. Documented nuance: `-Dorg.gradle.project.<key>` and
     * `~/.gradle/gradle.properties` resolve at that layer too, i.e. below the dedicated files —
     * which is why that layer's origin label names the whole fused group.
     */
    private fun labeledSources(
        target: Project,
        key: String,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): List<Pair<String, SelectionSource>> {
        val cli: String? = target.gradle.startParameter.projectProperties[key]
        return listOf(
            OriginLabels.cli(key) to SelectionSource { cli },
            OriginLabels.environmentVariable(key) to
                EnvironmentVariableSource(target.providers, ConfigKeys.ENV_PREFIX + key),
            ConfigKeys.LOCAL_FILE to SelectionSource { personal?.get(key) },
            ConfigKeys.COMMITTED_FILE to SelectionSource { committed?.get(key) },
            OriginLabels.gradleProperties(key) to GradlePropertySource(target.providers, key),
            OriginLabels.LOCAL_PROPERTIES to localPropertiesSource(target, key),
        )
    }

    /** The [labeledSources] chain composed into a plain first-non-null value source. */
    private fun configSources(
        target: Project,
        key: String,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): SelectionSource =
        composeSelectionSources(
            *labeledSources(target, key, personal, committed).map { it.second }.toTypedArray()
        )

    /**
     * Reads the global `kmptargets.hierarchyTemplate` flag through the standard [configSources]
     * chain. `null` means "not set — use the built-in default"; anything other than `true`/`false`
     * (case-insensitive) is treated as unset.
     */
    private fun hierarchyTemplateEnabledGlobally(
        target: Project,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): Boolean? =
        configSources(target, ConfigKeys.HIERARCHY_TEMPLATE, personal, committed)
            .read()
            ?.trim()
            ?.lowercase()
            ?.toBooleanStrictOrNull()

    /**
     * Reads the global `kmptargets.hierarchyCollapse` flag (issue #50) through the standard
     * [configSources] chain, with the same `null` = "not set" semantics as the template flag.
     */
    private fun hierarchyCollapseEnabledGlobally(
        target: Project,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): Boolean? =
        configSources(target, ConfigKeys.HIERARCHY_COLLAPSE, personal, committed)
            .read()
            ?.trim()
            ?.lowercase()
            ?.toBooleanStrictOrNull()

    /**
     * Reads the global `kmptargets.strict` flag through the standard [configSources] chain.
     * Deliberately opt-in: unset — or anything other than `true`/`false` (case-insensitive, per
     * `toBooleanStrictOrNull`) — means OFF, the advisory-only behavior.
     */
    private fun strictModeEnabled(
        target: Project,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): Boolean =
        configSources(target, ConfigKeys.STRICT, personal, committed)
            .read()
            ?.trim()
            ?.lowercase()
            ?.toBooleanStrictOrNull() ?: false

    /**
     * Reads the global `kmptargets.umbrellaTasks` flag (#77) through the standard [configSources]
     * chain. Opt-in: unset — or anything other than `true`/`false` (case-insensitive, per
     * `toBooleanStrictOrNull`) — means OFF, so the `kmpCompileAll`/`kmpTestAll` tasks are not
     * registered.
     */
    private fun umbrellaTasksEnabled(
        target: Project,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): Boolean =
        configSources(target, ConfigKeys.UMBRELLA_TASKS, personal, committed)
            .read()
            ?.trim()
            ?.lowercase()
            ?.toBooleanStrictOrNull() ?: false

    /** The parsed entries of a dedicated config file in the root directory, or `null` if absent. */
    private fun configFile(target: Project, fileName: String): Map<String, String>? =
        target.providers
            .of(ConfigFileValueSource::class.java) {
                it.parameters.rootDir.set(target.rootDir)
                it.parameters.fileName.set(fileName)
            }
            .orNull

    /**
     * Fails the build when a dedicated config file carries a key outside [ConfigKeys.ALL] — a typo
     * must not silently no-op (Bazel's "fail loud on unknown key" parity, issue #30).
     */
    private fun validateKnownKeys(entries: Map<String, String>?, fileName: String) {
        if (entries == null) return
        val unknown = entries.keys - ConfigKeys.ALL
        if (unknown.isEmpty()) return
        val described =
            unknown.sorted().joinToString(", ") { key ->
                val suggestion = didYouMean(key, ConfigKeys.ALL)
                if (suggestion != null) "'$key' (did you mean '$suggestion'?)" else "'$key'"
            }
        throw GradleException(
            "$fileName: unknown key(s) $described. " +
                "Known keys: ${ConfigKeys.ALL.sorted().joinToString(", ")}"
        )
    }

    private fun localPropertiesSource(target: Project, propertyName: String): SelectionSource {
        val provider =
            target.providers.of(LocalPropertyValueSource::class.java) {
                it.parameters.rootDir.set(target.rootDir)
                it.parameters.propertyName.set(propertyName)
            }
        return SelectionSource { provider.orNull }
    }

    private fun parseOrThrow(raw: String, propertyName: String): KmpTargetSet =
        when (val r = parseKmpTargets(raw)) {
            is ParseResult.Ok -> r.set
            is ParseResult.Err -> throw GradleException("$propertyName: ${r.message}")
        }

    /**
     * Registers `selection ∩ supported`, emits the empty-overlap and host-impossible advisories,
     * and applies the minimal hierarchy template — all off the cumulative supported set, so
     * repeated `supports` calls only register the delta (idempotent), each host warning names a
     * leaf at most once, and the template still applies at most once.
     *
     * When [strict] is on (#34), the advisories fail the build instead of warning — with the
     * identical message text. Severity changes; policy does not: `shouldWarnEmptyOverlap`,
     * `shouldWarnAndroidWithoutAgp`, `impossibleOnHost`, and `freshDeprecated` stay the single
     * source of truth for *whether* to flag. What registers is never affected — with one deliberate
     * exception: `androidTarget` without an Android Gradle plugin is skipped in both modes (#51),
     * because registering it is a hard KGP failure, not a choice.
     */
    private fun register(
        project: Project,
        ext: KmpTargetsExtension,
        globalHierarchyEnabled: Boolean?,
        globalCollapseEnabled: Boolean?,
        strict: Boolean,
    ) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        // Install the gradleName → live KotlinTarget resolver that backs onAppleTarget /
        // appleFramework (#105). Set before the registration loop below, so it is in place by the
        // time recordRegistration fires the onRegistered subscriptions those hooks ride. Idempotent
        // across supports{} passes; closes over the config-time KMP extension only, never a
        // Project,
        // and is invoked solely from configuration-time onRegistered actions — config-cache safe.
        ext.kotlinTargetByName = { name -> kotlin.targets.findByName(name) }
        val selection = ext.resolvedSelection()
        val supported = ext.resolvedSupported()
        val active = selection intersect supported
        // Read once per registration pass, as a plain String — config-cache safe (issue #49). The
        // `targetName` sugar guarantees this was set before the jvm leaf registered.
        val jvmName: String? = ext.jvmTargetName.orNull
        // Read fresh on every supports{} pass, so AGP applied between two calls counts for the
        // later pass.
        val androidPluginApplied = isAndroidPluginApplied(project)
        (active.members - ext.registeredLeaves).forEach { leaf ->
            // KGP's androidTarget() reports a FATAL diagnostic (AndroidGradlePluginIsMissing,
            // thrown immediately) when no Android Gradle plugin is applied. Skip the leaf instead
            // — the advisory below names the module and the fix — and do NOT record it, so a
            // later supports{} pass after AGP is applied still registers it.
            if (leaf == KmpTarget.Jvm.Android && !androidPluginApplied) return@forEach
            val kotlinTarget = registerTarget(kotlin, leaf, jvmName)
            val gradleName = kotlinTarget.targetName
            // Wire the umbrella tasks (#77) off the live target before recording: the dependency is
            // the genuine compile/test task, so it is rename-proof and never a silent zero-match.
            wireUmbrellas(ext, leaf, kotlinTarget)
            // Leaf first, then the carrier+callbacks (issue #52): an onRegistered action that
            // re-enters register() must already see this leaf as registered.
            ext.registeredLeaves.add(leaf)
            ext.recordRegistration(leaf, gradleName)
        }

        // The selection is non-empty yet genuinely disjoint from the supported set, so nothing
        // registers. Keyed off the actual overlap (not "nothing registered"), so the message can
        // never name a token present in both lists (issue #10). Safe to fail here under strict:
        // the intersection is empty, so no target half-registered before the throw.
        if (shouldWarnEmptyOverlap(selection, supported)) {
            warnOrFail(strict, emptyOverlapWarning(project.path, selection, supported)) {
                project.logger.warn(it)
            }
        }

        // Android-without-AGP advisory (#51): the one family member that filters — androidTarget
        // genuinely did not register (the loop above skipped it; the alternative is KGP's raw
        // FATAL), so the message says exactly that. Mutually exclusive with empty-overlap (android
        // active ⇒ the overlap is non-empty). Ordered before host/deprecated: under strict,
        // "an asked-for target was NOT registered" beats "registered but uncompilable here" beats
        // "registered but deprecated". Deduped once per module via the boolean flag (a set would
        // be overkill for a single leaf), recorded AFTER warnOrFail (mirroring the host step) so
        // a strict failure leaves no bookkeeping behind.
        if (!ext.androidAgpWarned && shouldWarnAndroidWithoutAgp(active, androidPluginApplied)) {
            warnOrFail(strict, androidWithoutAgpWarning(project.path)) { project.logger.warn(it) }
            ext.androidAgpWarned = true
        }

        // Host-awareness (#32): names registered native targets this host cannot compile, never
        // changing what registers (the set stays identical across hosts). HostManager is touched
        // only here, inside `withPlugin(KGP_ID)` at configuration time, so its classes never load
        // when KGP is absent and nothing host-derived is held by a task action. Deduped per leaf
        // via `hostWarned`, so a later `supports` union that introduces a new impossible leaf
        // still flags — for that leaf only. The decision lives behind an enabled-set parameter
        // (`enforceHostCompatibility`) so strict-mode tests can simulate any host.
        ext.hostWarned +=
            enforceHostCompatibility(
                path = project.path,
                active = active,
                enabled = HostManager().enabled.toSet(),
                host = HostManager.host,
                alreadyWarned = ext.hostWarned,
                strict = strict,
            ) {
                project.logger.warn(it)
            }

        // Deprecation advisory (#43): Kotlin's docs mark leaves deprecated but KGP (2.3.21) emits
        // no configuration-time signal when they register, so this plugin does. Keyed off the
        // ACTIVE set — a deprecated leaf the module doesn't support never registers, so it is
        // never flagged. Signal only, never filtering; deduped per leaf via `deprecatedWarned`,
        // recorded AFTER warnOrFail (mirroring the host step) so a strict failure leaves no
        // bookkeeping behind. Ordered after the host advisory: under strict, "this machine can't
        // build it" beats "the ecosystem is sunsetting it". If a future KGP version emits its own
        // deprecation warning, delete this advisory.
        val freshDeprecated = freshDeprecated(active, ext.deprecatedWarned)
        if (freshDeprecated.isNotEmpty()) {
            warnOrFail(
                strict,
                deprecatedTargetsWarning(
                    project.path,
                    KmpTargetSet.of(*freshDeprecated.toTypedArray()),
                ),
            ) {
                project.logger.warn(it)
            }
            ext.deprecatedWarned += freshDeprecated
        }

        // Inert-module advisory (#71): the pass ended with zero registrations for a module that
        // declared supports { } — KGP still materializes the commonMain metadata compilation,
        // which fails on a platform-less module, so the doomed task deserves a signal. Keyed off
        // registered() — the exact query the message tells build-logic to gate on — so it covers
        // every cause uniformly: a disjoint overlap, an explicitly-empty selection (where
        // empty-overlap is silent by design, issue #9), and an android-only overlap skipped by the
        // #51 guard. Ordered LAST on purpose: under strict the cause advisories above (empty-
        // overlap, android-without-AGP) win the exception, and inert — the consequence channel —
        // is the thrown text only where no cause advisory covers the state. Deduped once per
        // module (registration is one-way, so a module that un-inerts can never re-inert),
        // recorded AFTER warnOrFail so a strict failure leaves no bookkeeping behind.
        if (
            !ext.inertWarned &&
                shouldWarnInertModule(ext.supportsProperty.isPresent, ext.registered())
        ) {
            warnOrFail(strict, inertModuleWarning(project.path)) { project.logger.warn(it) }
            ext.inertWarned = true
        }

        // Native-only-metadata advisory (#72): the module SUPPORTS the JVM family (androidTarget
        // and/or jvm) yet this pass ends with no JVM-family leaf registered while OTHER targets
        // did register. The module is alive — the platform klibs compile — but commonMain
        // collapses to a JVM-less shared fragment, so the *KotlinMetadata* compilations reject
        // JVM-flavored constructs (@JvmInline and friends): klibs build, metadata fails. Keyed off
        // resolvedSupported() (not the active overlap), so an android leaf the #51 guard skipped
        // still counts as "supports JVM, registered none". The registered-non-empty conjunct makes
        // this disjoint from inert (#71, registered empty) by construction — exactly one of the
        // two consequence advisories fires per module state, so their relative order can never
        // matter under strict; inert stays textually first (whole-module-dead before
        // fragment-flavor). Like inert, it is a consequence channel evaluated last: under strict
        // the cause advisories above (empty-overlap, android-without-AGP) win the exception.
        // Deduped once per module, recorded AFTER warnOrFail so a strict failure leaves no
        // bookkeeping behind.
        if (
            !ext.nativeOnlyMetadataWarned &&
                shouldWarnNativeOnlyMetadata(ext.resolvedSupported(), ext.registered())
        ) {
            warnOrFail(strict, nativeOnlyMetadataWarning(project.path)) { project.logger.warn(it) }
            ext.nativeOnlyMetadataWarned = true
        }

        // Framework-unattached advisory (#107): a framework is declared but this selection attached
        // it to no Apple leaf in its `on` scope (jvm-only, or an `on` narrower than what
        // registered),
        // so it never materializes and an Xcode build consuming it fails downstream. LAST in the
        // consequence channel: under strict the cause advisories and the more fundamental
        // inert/JVM-less consequences above win the exception; this is the most specific
        // consequence
        // and reads last. The helper is shared with the appleFramework() replay hook so the firing
        // is order-independent; it dedups and records bookkeeping AFTER warnOrFail.
        maybeWarnFrameworkUnattached(project, ext, strict)

        // Deliberately receives the unfiltered active set even when android was skipped above:
        // android is ungrouped in the hierarchy taxonomy (it attaches straight to common and
        // never affects the native collapse), so filtering would change nothing.
        maybeApplyHierarchyTemplate(
            project,
            ext,
            active,
            globalHierarchyEnabled,
            globalCollapseEnabled,
        )
    }

    /**
     * Applies the minimal hierarchy template for [active], at most once per module. Calling
     * `applyHierarchyTemplate` is itself what suppresses KGP's costly default: KGP only
     * auto-applies its default when no template has been applied yet. When disabled (or the active
     * set is empty), we apply nothing and let KGP fall back to its default.
     */
    private fun maybeApplyHierarchyTemplate(
        project: Project,
        ext: KmpTargetsExtension,
        active: KmpTargetSet,
        globalEnabled: Boolean?,
        globalCollapseEnabled: Boolean?,
    ) {
        if (ext.hierarchyTemplateApplied || active.isEmpty()) return
        if (!resolveHierarchyTemplateEnabled(ext.hierarchyTemplate.orNull, globalEnabled)) return
        // The collapse knob (issue #50) only matters when the minimal template itself applies —
        // with the template disabled, KGP's default hierarchy owns the tree.
        val collapse =
            resolveHierarchyCollapseEnabled(ext.collapseHierarchy.orNull, globalCollapseEnabled)
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.applyHierarchyTemplate(computeHierarchySpec(active, collapse).toTemplate())
        ext.hierarchyTemplateApplied = true
    }

    /**
     * Registers [target] with KGP and returns the `KotlinTarget` the factory created. Its
     * [targetName][KotlinTarget.getTargetName] is the Gradle registration actually used — never
     * re-derived from the leaf, so the
     * [RegisteredTarget][com.rsicarelli.kmptargets.model.RegisteredTarget] carriers stay truthful
     * for the renamed jvm leaf (issue #49) and for whatever name KGP picks. The live target is also
     * what [wireUmbrellas] reads to find the genuine compile/test tasks (#77).
     */
    private fun registerTarget(
        kotlin: KotlinMultiplatformExtension,
        target: KmpTarget,
        jvmName: String?,
    ): KotlinTarget =
        when (target) {
            KmpTarget.Jvm.Android -> kotlin.androidTarget()
            // The only renamable leaf (issue #49): KGP's hierarchy matchers (`withJvm()`) key
            // off the platform type, so a custom-named jvm target attaches exactly like the
            // default.
            KmpTarget.Jvm.Desktop -> if (jvmName != null) kotlin.jvm(jvmName) else kotlin.jvm()
            KmpTarget.Native.Apple.Ios.Arm64 -> kotlin.iosArm64()
            KmpTarget.Native.Apple.Ios.SimulatorArm64 -> kotlin.iosSimulatorArm64()
            KmpTarget.Native.Apple.Ios.X64 -> kotlin.iosX64()
            KmpTarget.Native.Apple.Macos.Arm64 -> kotlin.macosArm64()
            KmpTarget.Native.Apple.Macos.X64 -> kotlin.macosX64()
            KmpTarget.Native.Apple.Watchos.Arm64 -> kotlin.watchosArm64()
            KmpTarget.Native.Apple.Watchos.Arm32 -> kotlin.watchosArm32()
            KmpTarget.Native.Apple.Watchos.X64 -> kotlin.watchosX64()
            KmpTarget.Native.Apple.Watchos.SimulatorArm64 -> kotlin.watchosSimulatorArm64()
            KmpTarget.Native.Apple.Watchos.DeviceArm64 -> kotlin.watchosDeviceArm64()
            KmpTarget.Native.Apple.Tvos.Arm64 -> kotlin.tvosArm64()
            KmpTarget.Native.Apple.Tvos.X64 -> kotlin.tvosX64()
            KmpTarget.Native.Apple.Tvos.SimulatorArm64 -> kotlin.tvosSimulatorArm64()
            KmpTarget.Native.Linux.X64 -> kotlin.linuxX64()
            KmpTarget.Native.Linux.Arm64 -> kotlin.linuxArm64()
            KmpTarget.Native.Mingw.X64 -> kotlin.mingwX64()
            KmpTarget.Native.AndroidNative.Arm32 -> kotlin.androidNativeArm32()
            KmpTarget.Native.AndroidNative.Arm64 -> kotlin.androidNativeArm64()
            KmpTarget.Native.AndroidNative.X86 -> kotlin.androidNativeX86()
            KmpTarget.Native.AndroidNative.X64 -> kotlin.androidNativeX64()
            KmpTarget.Web.Js ->
                kotlin.js {
                    browser()
                    nodejs()
                }
            KmpTarget.Web.WasmJs ->
                kotlin.wasmJs {
                    browser()
                    nodejs()
                }
            KmpTarget.Web.WasmWasi -> kotlin.wasmWasi { nodejs() }
        }
}

/**
 * Strict mode's single escalation point (#34): warn when [strict] is off — byte-for-byte the
 * advisory behavior — and fail with the **same** [message] when on. Severity changes; the decision
 * of *whether* to flag stays with the callers' policy functions.
 */
internal fun warnOrFail(strict: Boolean, message: String, warn: (String) -> Unit) {
    if (strict) throw GradleException(message)
    warn(message)
}

/**
 * Whether to emit [emptyOverlapWarning]: only when the selection is non-empty yet genuinely
 * disjoint from the supported set. Keyed off the actual overlap (not "nothing registered with
 * KGP"), so the message can never name a token present in both lists (issue #10).
 */
internal fun shouldWarnEmptyOverlap(selection: KmpTargetSet, supported: KmpTargetSet): Boolean =
    selection.isNotEmpty() && (selection intersect supported).isEmpty()

internal fun emptyOverlapWarning(
    path: String,
    selection: KmpTargetSet,
    supported: KmpTargetSet,
): String =
    "kmp-targets: '$path' supports ${ids(supported)} but the selection ${ids(selection)} " +
        "matches none of them — registering no targets for this module."

/**
 * KGP's own Android-plugin id list (`org.jetbrains.kotlin.gradle.utils.androidPluginIds`), mirrored
 * verbatim so the registration guard skips `androidTarget` on exactly the modules where KGP's
 * `androidTarget()` would fail — and never on ones (dynamic-feature, test, …) where it succeeds.
 * Pinned by a test against narrowing to the two common ids.
 */
internal val androidPluginIds: List<String> =
    listOf(
        "com.android.application",
        "com.android.library",
        "com.android.dynamic-feature",
        "com.android.test",
        "com.android.instantapp",
        "com.android.feature",
    )

/** Whether any Android Gradle plugin is applied to [project] — a cheap plugin-manager lookup. */
internal fun isAndroidPluginApplied(project: Project): Boolean = androidPluginIds.any {
    project.pluginManager.hasPlugin(it)
}

/**
 * Whether to emit [androidWithoutAgpWarning]: `androidTarget` is in the active (registering) set
 * but no Android Gradle plugin is applied, so the leaf cannot register — KGP's `androidTarget()` is
 * a FATAL diagnostic without AGP. Pure over its inputs, so it is unit-testable without Gradle; the
 * same decision drives the `kmpTargetsInfo` skipped annotation.
 */
internal fun shouldWarnAndroidWithoutAgp(
    active: KmpTargetSet,
    androidPluginApplied: Boolean,
): Boolean = KmpTarget.Jvm.Android in active.members && !androidPluginApplied

/**
 * The android-without-AGP advisory (#51). Mirrors its siblings in shape; serves as both the warning
 * and the strict-mode failure text through [warnOrFail]. Unlike them it reports a leaf that was
 * genuinely NOT registered, and names the fix — the ordering rule that the Android plugin must be
 * applied before `supports { }` runs. The closing clause (#76) makes it the discovery vehicle for
 * the selection-gated idiom: apply AGP only when android is selected, and link the recipe that
 * covers the `com.android.application` exemption and the downstream-read traps the gate must guard.
 */
internal fun androidWithoutAgpWarning(path: String): String =
    "kmp-targets: '$path' selects and supports [androidTarget] but no Android Gradle plugin is " +
        "applied — the target was not registered. Apply com.android.library or " +
        "com.android.application before supports { } — gate it on the selection " +
        "(see https://rsicarelli.github.io/kmp-targets/user-guide/recipes/#selection-gated-eager-agp-application)."

/**
 * The deprecated leaves of [active] not yet flagged for this module — the dedup math of the
 * deprecation advisory (#43), pure so it is unit-testable without Gradle. Keyed off the active
 * (registered) set on purpose: a deprecated leaf the module never registers is nobody's problem.
 */
internal fun freshDeprecated(active: KmpTargetSet, alreadyWarned: Set<KmpTarget>): Set<KmpTarget> =
    active.members.filterTo(mutableSetOf()) { it in KmpTarget.deprecated } - alreadyWarned

/**
 * Single aggregated deprecation advisory naming the [deprecated] ids (sorted). Mirrors
 * [emptyOverlapWarning]/`hostImpossibleWarning` in shape; serves as both the warning and the
 * strict-mode failure text through `warnOrFail`, so the two can never drift apart. The version
 * stamp must track the `KmpTarget.deprecated` overrides (see the model KDoc).
 */
internal fun deprecatedTargetsWarning(path: String, deprecated: KmpTargetSet): String =
    "kmp-targets: '$path' registers ${ids(deprecated)} which Kotlin marks deprecated " +
        "(since Kotlin 2.3.20, see https://kotlinlang.org/docs/native-target-support.html) — " +
        "still registered (selection is unchanged), but consider migrating off them."

/**
 * Whether to emit [inertModuleWarning] (#71): the module declared `supports { }` yet ended a
 * registration pass with zero actual registrations. Keyed off [registered] — the
 * `KmpTargetsExtension.registered()` snapshot, i.e. what truly registered with KGP — rather than
 * `selection ∩ supported`, so the condition is provably the same one the message tells build-logic
 * to gate on, and the #51 android skip counts as inert. Deliberately host-free: registration is
 * host-blind, so a registered-but-uncompilable module is NOT inert on any host. A module that never
 * declared `supports { }` registers nothing *intentionally* (explicit-selection doctrine) and is
 * never flagged. Pure over its inputs, so it is unit-testable without Gradle; the same decision
 * drives the `kmpTargetsInfo` inert line.
 */
internal fun shouldWarnInertModule(supportsDeclared: Boolean, registered: KmpTargetSet): Boolean =
    supportsDeclared && registered.isEmpty()

/**
 * The inert-module advisory (#71). Mirrors its siblings in shape; serves as both the warning and
 * the strict-mode failure text through [warnOrFail]. Unlike them it names a *task-level
 * consequence* — KGP materializes `compileCommonMainKotlinMetadata` for every module applying the
 * KMP plugin, and that compilation fails when no platform target exists — and the build-logic gate.
 * Deliberately set-free: the advisory is cause-agnostic (disjoint overlap, explicitly-empty
 * selection, android-only AGP skip all fire it), so naming the selection or supported sets would
 * mislead in at least one of those cases; the cause advisories above it name the sets.
 */
internal fun inertModuleWarning(path: String): String =
    "kmp-targets: '$path' declared supports { } but registered zero targets — the module is " +
        "inert. KGP still materializes the commonMain metadata compilation, which fails with no " +
        "platform targets. Gate it in build-logic when kmpTargets.registered().isEmpty()."

/**
 * Whether to emit [nativeOnlyMetadataWarning] (#72): the module supports the JVM family
 * ([KmpTargetSet.jvmFamily]) yet registered no JVM-family leaf while registering at least one other
 * target. Keyed off [supported] — `resolvedSupported()`, not the active overlap — so a
 * selected-but-skipped android leaf (the #51 guard) still counts as "supports JVM, registered
 * none"; and off [registered] — the actual registrations — which is leaf-based, so a jvm leaf
 * renamed via `targetName` (issue #49) still keeps the fragment JVM-flavored. The
 * registered-non-empty conjunct makes it disjoint from [shouldWarnInertModule] (#71, registered
 * empty): an inert module is whole-module-dead, this one is alive with a JVM-less commonMain. Any
 * single JVM-family leaf registered — android-only or jvm-only — defeats the predicate (Android is
 * a JVM platform). Deliberately host-free: registration is host-blind, and a JVM-less fragment is
 * JVM-less on every host. Pure over its inputs, so it is unit-testable without Gradle; the same
 * decision drives the `kmpTargetsInfo` jvm-less line.
 */
internal fun shouldWarnNativeOnlyMetadata(
    supported: KmpTargetSet,
    registered: KmpTargetSet,
): Boolean =
    (supported intersect KmpTargetSet.jvmFamily).isNotEmpty() &&
        registered.isNotEmpty() &&
        (registered intersect KmpTargetSet.jvmFamily).isEmpty()

/**
 * The native-only-metadata advisory (#72). Mirrors its siblings in shape; serves as both the
 * warning and the strict-mode failure text through [warnOrFail]. Like inert (#71) it names a
 * *task-level consequence* — here the paradoxical half-failure where every platform klib compiles
 * but the `*KotlinMetadata*` compilations reject JVM-flavored constructs — and it deliberately
 * names the greppable symptom (`@JvmInline`) so a user arriving from the raw compiler error finds
 * it. The gate it points at is the *scoped* sibling of inert's: disable only the metadata
 * compilations, keep the alive platform ones. Deliberately set-free: the advisory is cause-agnostic
 * (a narrowed lane and an android-only AGP skip fire it alike), so naming the selection or
 * supported sets would mislead in at least one of those cases; the cause advisories above it name
 * the sets.
 */
internal fun nativeOnlyMetadataWarning(path: String): String =
    "kmp-targets: '$path' supports the JVM family but this selection registered no JVM-family " +
        "target while other targets did — commonMain is now a JVM-less shared fragment. The " +
        "platform klibs compile, but the *KotlinMetadata* compilations reject JVM-flavored " +
        "constructs (e.g. @JvmInline): klibs build, metadata fails. Gate it in build-logic when " +
        "kmpTargets.registered(jvmFamily).isEmpty(), disabling only the *KotlinMetadata* " +
        "compilations."

/**
 * Whether to emit [frameworkUnattachedWarning] (#107): an `appleFramework` was declared, this
 * module declared `supports { }`, yet [attachedLeaves] — the Apple leaves the framework actually
 * attached to (`registered ∩ on ∩ apple`) — is empty. Keyed off the real attached set rather than
 * the issue's literal `registered(apple)`, so a framework scoped via `on` to leaves the selection
 * never registered is flagged even when other Apple leaves did register (e.g. `on = appleMobile`
 * with only macOS registered). The `supportsDeclared` conjunct upholds the explicit-selection
 * doctrine: a module that never declared `supports { }` registered nothing intentionally and is
 * never flagged (the doctor still renders the declared-but-unattached facts). Pure over its inputs,
 * so it is unit-testable without Gradle; the same decision drives the doctor finding.
 */
internal fun shouldWarnFrameworkUnattached(
    frameworkDeclared: Boolean,
    supportsDeclared: Boolean,
    attachedLeaves: KmpTargetSet,
): Boolean = frameworkDeclared && supportsDeclared && attachedLeaves.isEmpty()

/**
 * The framework-unattached advisory (#107). Mirrors its siblings in shape; serves as both the
 * warning and the strict-mode failure text through [warnOrFail], so the two can never drift. Names
 * the module, the framework ([baseName]), the gate (no Apple target in the framework's scope
 * registered), the downstream consequence (an Xcode build consuming it fails with a missing
 * framework), and the fix. Strict mode is the point: a release lane with `kmptargets.strict` and a
 * misconfigured selection fails loudly at configuration instead of shipping a frameworkless
 * artifact that breaks Xcode far downstream.
 */
internal fun frameworkUnattachedWarning(path: String, baseName: String): String =
    "kmp-targets: '$path' declared appleFramework(\"$baseName\") but this selection registered no " +
        "Apple target in its scope — the framework attaches to nothing and never builds, so an " +
        "Xcode build consuming it fails downstream with a missing-framework error. Widen the " +
        "selection (or this module's supports { }) to register an Apple target in the framework's " +
        "`on` scope, or gate the appleFramework(…) declaration in build-logic when " +
        "kmpTargets.registered(apple).isEmpty()."

/**
 * Whether the `single-target KSP` **doctor finding** fires: a KSP plugin is applied yet exactly one
 * target registered. The shared commonMain metadata route (`kspCommonMainKotlinMetadata`) exists
 * only when ≥2 platform targets share commonMain — the same target-count rule as
 * `compileCommonMainKotlinMetadata` (pinned by `CommonMainMetadataCompilationInvariantTest`), and
 * confirmed for KSP itself by the ktorfit sample; native presence is irrelevant. So a single
 * registered target has no commonMain KSP route, and a processor that generates into commonMain is
 * skipped.
 *
 * Unlike the other `shouldWarn*` predicates this drives **only** the doctor finding — there is
 * deliberately no advisory or strict-mode escalation, because the second conjunct of the real trap
 * ("does this module actually generate into commonMain?") is unobservable (the #73 doctrine). The
 * plugin observes only the two facts it can — KSP applied, one target — and flags the risk. Pure
 * over its inputs, so it is unit-testable without Gradle.
 */
internal fun shouldWarnSingleTargetKsp(kspApplied: Boolean, registered: KmpTargetSet): Boolean =
    kspApplied && registered.size == 1

/**
 * The ABI × selection narrowing advisory (#81). Fired from an ABI task's `doFirst` — so it names
 * the exact [task] the developer just ran — when the run only covers part of this module's
 * supported set ([uncovered] = `supported − registered`, never empty here). Mirrors its siblings in
 * shape and doubles as the strict-mode failure text through [warnOrFail]. Deliberately points at
 * the team's full-selection CI lane as the real safety net: this signal exists so a local narrowed
 * run is never silent, not to replace the check that owns the whole ABI.
 */
internal fun abiNarrowingWarning(path: String, task: String, uncovered: List<String>): String =
    "kmp-targets: '$path' is running '$task' under a narrowed selection — it covers only the " +
        "registered targets, so the ABI of ${uncovered.joinToString(", ")} is NOT dumped/validated " +
        "by this run. Run it under the full selection (or the lane that owns each target); your " +
        "team's full-selection CI lane is the safety net. (strict mode fails this.)"

/**
 * The supported leaves the current selection left unregistered AND whose **ABI group** has no
 * registered representative (#81), as sorted ids — the precise gate for the narrowing advisory. A
 * leaf is deliberately *not* flagged when a same-ABI-group sibling registered: with `iosArm64 +
 * iosSimulatorArm64` supported but only `iosSimulatorArm64` registered, the iOS ABI surface is
 * still dumped/validated by the simulator (KLIB dumps share declarations across a family and infer
 * siblings), so warning about `iosArm64` would be a false alarm that trains users to ignore the
 * signal. A whole family with no registered member (e.g. `linuxX64`/`js` while only `jvm`
 * registered) is still flagged. Pure over its inputs, so it is unit-testable without Gradle.
 */
internal fun abiUncoveredGroups(supported: KmpTargetSet, registered: KmpTargetSet): List<String> {
    val coveredGroups = registered.members.mapTo(mutableSetOf(), ::abiGroupOf)
    return (supported.members - registered.members)
        .filterNot { abiGroupOf(it) in coveredGroups }
        .map { it.id }
        .sorted()
}

/**
 * The ABI group a leaf belongs to — the granularity at which KLIB ABI dumps share declarations and
 * infer siblings (#81). Native targets group by konan family (every iOS leaf shares one ABI, every
 * linux leaf another, the four androidNative arches one); the JVM-backed leaves (`jvm`,
 * `androidTarget`) and each web leaf are distinct ABIs, so each is its own group. Derived from the
 * sealed hierarchy, so it loads no konan classes (safe even when KGP is absent).
 */
internal fun abiGroupOf(leaf: KmpTarget): String =
    when (leaf) {
        is KmpTarget.Native.Apple.Ios -> "ios"
        is KmpTarget.Native.Apple.Macos -> "macos"
        is KmpTarget.Native.Apple.Watchos -> "watchos"
        is KmpTarget.Native.Apple.Tvos -> "tvos"
        is KmpTarget.Native.Linux -> "linux"
        is KmpTarget.Native.Mingw -> "mingw"
        is KmpTarget.Native.AndroidNative -> "androidNative"
        is KmpTarget.Jvm,
        is KmpTarget.Web -> leaf.id
    }

private fun ids(set: KmpTargetSet): List<String> = set.members.map { it.id }.sorted()

/** The deprecated leaves as a set, for doctor's `registered() ∩ deprecated` finding (#43). */
private val deprecatedSet: KmpTargetSet = KmpTargetSet.of(*KmpTarget.deprecated.toTypedArray())
