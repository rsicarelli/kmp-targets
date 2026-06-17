package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.model.RegisteredTarget
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

/**
 * Public DSL surface exposed by the plugin as the `kmpTargets` extension.
 *
 * The plugin separates two facts:
 * - **supported** — what *this module* can build, declared per-module by the type-safe [supports]
 *   block (or its raw value overload). It is **explicit**: a module that never calls [supports]
 *   registers no targets at all (just like plain KGP, where every target is declared by hand).
 *   [resolvedSupported] returns [KmpTargetSet.empty] until something declares it.
 * - **selection** — what the user wants to build *now*. It is **global**: resolved from
 *   `kmptargets.targets` (`-P`, env, the dedicated `kmp-targets(.local).properties` files, root
 *   `gradle.properties`, or `local.properties`) and the same value for every module. When no global
 *   is provided, it falls back to [defaultSelection] (itself defaulting to [KmpTargetSet.all]).
 *
 * The plugin registers `selection ∩ supported`. Registration is **eager**: calling [supports] from
 * the build-script body registers the matching targets immediately (no deferred/after-evaluate
 * pass), so anything that reads `kotlin.targets` afterwards sees them. The extension only ever
 * holds immutable `KmpTargetSet`s of `data object` leaves, so it is configuration-cache safe — no
 * `Project` or task state is captured.
 */
public abstract class KmpTargetsExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * The selection used when no global `kmptargets.targets` is provided. Defaults to
     * [KmpTargetSet.all]. Public so build-logic can set a project-wide default lazily; a global
     * `kmptargets.targets` always wins over it. Set it **before** [supports], since [supports]
     * registers eagerly off the resolved selection.
     */
    public abstract val defaultSelection: Property<KmpTargetSet>

    /**
     * Per-project opt-out of the minimal custom hierarchy template. Unset defers to the global
     * `kmptargets.hierarchyTemplate` Gradle property, which itself defaults to `true`. When
     * `false`, the plugin applies no template and KGP falls back to its own
     * `applyDefaultHierarchyTemplate()` — also the escape hatch for a module that supplies its own
     * `applyHierarchyTemplate { … }`.
     *
     * Set it **before** [supports]: the template is applied as part of the eager registration that
     * [supports] triggers, so it must already hold its value by then.
     */
    public abstract val hierarchyTemplate: Property<Boolean>

    /**
     * Per-project opt-out of the single-child collapse rule (issue #50). Unset defers to the global
     * `kmptargets.hierarchyCollapse` property, which itself defaults to `true` (collapse — the
     * minimal tree). When `false`, a group materializes whenever it has ≥1 present child, so
     * intermediates like `iosMain` survive a single-leaf selection — for codebases with
     * load-bearing `src/iosMain` dirs whose everyday workflow narrows to one iOS leaf. No effect
     * when [hierarchyTemplate] resolves to `false` (KGP's default template owns the tree then).
     *
     * Set it **before** [supports], for the same eager-registration reason as [hierarchyTemplate].
     */
    public abstract val collapseHierarchy: Property<Boolean>

    /**
     * Per-module override of the Gradle target **name** the jvm leaf registers under (issue #49).
     * Unset means the KGP default (`jvm`). Only the jvm leaf is renamable: `androidTarget`'s name
     * is fixed by AGP, and native/web names are derived by KGP — renaming them would break
     * konan/source-set conventions. The selection token is unchanged: a build still selects this
     * target with `jvm` (or its `desktop` alias); only the registered Gradle target, its source
     * sets (`desktopMain`), and the published artifact suffix follow the custom name.
     *
     * Set it **before** [supports] — the name is read during the eager registration that [supports]
     * triggers, and KGP target registration is one-way. Prefer the validated [targetName] sugar,
     * which also fails fast on ordering violations instead of silently never applying.
     */
    public abstract val jvmTargetName: Property<String>

    /**
     * Validated sugar over [jvmTargetName]:
     * ```kotlin
     * kmpTargets {
     *     targetName(KmpTargetsDsl.jvm, "desktop") // BEFORE supports { }
     *     supports { mobile + jvm }
     * }
     * ```
     *
     * Fails (instead of silently misbehaving) when [leaf] is not exactly the jvm leaf, when [name]
     * is blank, or when the jvm leaf already registered — registration is eager and one-way, so a
     * late rename could never apply.
     */
    public fun targetName(leaf: KmpTargetSet, name: String) {
        if (leaf.members.singleOrNull() != KmpTarget.Jvm.Desktop) {
            throw GradleException(
                "kmp-targets: only the jvm target is renamable (androidTarget is named by AGP; " +
                    "native and web target names are derived by KGP). " +
                    "Got: ${leaf.members.map { it.id }.sorted()}."
            )
        }
        if (name.isBlank()) {
            throw GradleException("kmp-targets: the jvm target name must not be blank.")
        }
        if (KmpTarget.Jvm.Desktop in registeredLeaves) {
            throw GradleException(
                "kmp-targets: targetName must be called before supports { } — the jvm leaf " +
                    "already registered under its default name and KGP registration is one-way."
            )
        }
        jvmTargetName.set(name)
    }

    /**
     * What this module can build. Unset means "not declared" → no targets register, and
     * [resolvedSupported] returns [KmpTargetSet.empty]. Written by the [supports] block (or its raw
     * overload), which unions on each call.
     */
    internal val supportsProperty: Property<KmpTargetSet> =
        objects.property(KmpTargetSet::class.java)

    /**
     * Fired by [supports]; the plugin wires this to eager registration. Null until the plugin runs.
     */
    internal var onSupports: (() -> Unit)? = null

    /**
     * Declares this module's supported set with the type-safe target vocabulary and **registers**
     * `selection ∩ supported` immediately:
     * ```kotlin
     * kmpTargets { supports { mobile + web - iosX64 } }
     * ```
     *
     * Must be called before anything reads `kotlin.targets` (registration is eager). Calling more
     * than once **unions** the sets — KGP target registration is one-way, so an already-registered
     * target cannot be retracted. To narrow, write the final set inside a single block.
     */
    public fun supports(block: KmpTargetsDsl.() -> KmpTargetSet) {
        supports(KmpTargetsDsl.block())
    }

    /** Raw overload for build-logic: `supports(KmpTargetSet.mobile + KmpTargetSet.web)`. */
    public fun supports(value: KmpTargetSet) {
        supportsProperty.set((supportsProperty.orNull ?: KmpTargetSet.empty) + value)
        onSupports?.invoke()
    }

    /**
     * The global selection resolved from `kmptargets.targets` at apply time, or `null` when no
     * global source provided one. When present it wins over [defaultSelection] (and an explicit
     * empty set is honored — see issue #9), which is what keeps the global switch authoritative.
     * Stored as an immutable [KmpTargetSet], so it stays configuration-cache safe.
     */
    internal var globalSelection: KmpTargetSet? = null

    /**
     * The selection actually used to register targets: the global `kmptargets.targets` if present,
     * otherwise [defaultSelection] (which itself defaults to [KmpTargetSet.all]). Public so
     * build-logic can read the resolved set (e.g. for conditional configuration).
     */
    public fun resolvedSelection(): KmpTargetSet = globalSelection ?: defaultSelection.get()

    /** Leaves already registered with KGP, so repeated registration stays idempotent. */
    internal val registeredLeaves: MutableSet<KmpTarget> = mutableSetOf()

    /** Leaves already named in a host-impossible warning, so each is warned at most once. */
    internal val hostWarned: MutableSet<KmpTarget> = mutableSetOf()

    /** Leaves already named in a deprecation advisory, so each is flagged at most once. */
    internal val deprecatedWarned: MutableSet<KmpTarget> = mutableSetOf()

    /** True once the minimal hierarchy template has been applied, so it is applied at most once. */
    internal var hierarchyTemplateApplied: Boolean = false

    /** True once the android-without-AGP advisory fired, so it fires at most once per module. */
    internal var androidAgpWarned: Boolean = false

    /**
     * True once the inert-module advisory (#71) fired, so it fires at most once per module. A
     * boolean (not a set) on purpose: inertness is a whole-module property, and registration is
     * one-way — once [registered] goes non-empty the module can never turn inert again.
     */
    internal var inertWarned: Boolean = false

    /**
     * True once the native-only-metadata advisory (#72) fired, so it fires at most once per module.
     * A boolean (not a set) on purpose: the JVM-less-fragment trap is a whole-module property, and
     * it resolves permanently once any JVM-family leaf registers (registration is one-way) — the
     * flag only suppresses repeat firing across [supports] unions while the condition persists.
     */
    internal var nativeOnlyMetadataWarned: Boolean = false

    /**
     * The resolved supported set: the declared value (unioned across [supports] calls), or
     * [KmpTargetSet.empty] if none was declared. Public so build-logic (and consumers) can read
     * what this module ended up declaring.
     */
    public fun resolvedSupported(): KmpTargetSet = supportsProperty.orNull ?: KmpTargetSet.empty

    /**
     * Every registration this plugin performed, in order, as [RegisteredTarget] carriers — the
     * single source for [registered] snapshots and [onRegistered] replay. Appended by
     * [recordRegistration] from the plugin's registration loop.
     */
    internal val registeredLog: MutableList<RegisteredTarget> = mutableListOf()

    /** Actions hooked via [onRegistered], fired for each future [recordRegistration]. */
    internal val onRegisteredActions: MutableList<(RegisteredTarget) -> Unit> = mutableListOf()

    /**
     * Resolves a registered target's `gradleName` to the **live KGP [KotlinTarget]**, so
     * [onAppleTarget] / [appleFramework] can route to the real type instead of re-deriving it. The
     * plugin installs it once inside `withPlugin(KGP_ID)` (where the `KotlinMultiplatformExtension`
     * exists); it stays null until then, so the apple hooks are inert when KGP is absent — which is
     * also when nothing registers and [onRegistered] never fires. A configuration-time-only
     * carrier: invoked solely from `onRegistered` actions, never held by a task action, so no
     * `Project` (or the KMP extension it closes over) is serialized into the configuration cache.
     */
    internal var kotlinTargetByName: ((String) -> KotlinTarget?)? = null

    /**
     * True once [appleFramework] has been called, so a second call fails loudly. One framework per
     * module in v1: KGP's binary names collide on the empty `namePrefix`, and multi-framework needs
     * a deliberate `namePrefix` strategy (a follow-up). The duplicate-fail keeps that door open.
     */
    internal var appleFrameworkDeclared: Boolean = false

    /**
     * The per-module `kmpCompileAll` umbrella task (#77), registered unconditionally at apply time.
     * The eager registration loop appends one compile-task dependency per registered leaf, so the
     * umbrella ends up depending on exactly the registered intersection — selection-agnostic and
     * rename-proof. A configuration-time-only carrier: it holds a [TaskProvider], never a
     * `Project`, and the umbrella itself declares no inputs, so nothing here is serialized into the
     * configuration cache. Nullable only defensively — `apply` always assigns it before [supports]
     * can fire.
     */
    internal var compileAllTask: TaskProvider<Task>? = null

    /**
     * The per-module `kmpTestAll` umbrella task (#77), the test-side sibling of [compileAllTask].
     * The registration loop appends the test-run task of each registered leaf that has one (the
     * `KotlinTargetWithTests` targets), so a renamed jvm leaf's `desktopTest` runs where a
     * hardcoded `jvmTest` would have silently matched nothing. Same configuration-time-only carrier
     * discipline.
     */
    internal var testAllTask: TaskProvider<Task>? = null

    /**
     * Records that [leaf] registered with KGP under [gradleName] and fires the [onRegistered]
     * actions. The log is appended *before* firing and the actions iterate a snapshot, so an action
     * that re-enters (e.g. by calling [supports]) sees a consistent log and never trips a
     * concurrent modification — though nested fire ordering is unspecified and re-entering is not a
     * supported pattern.
     */
    internal fun recordRegistration(leaf: KmpTarget, gradleName: String) {
        val carrier = RegisteredTarget(leaf, gradleName)
        registeredLog += carrier
        onRegisteredActions.toList().forEach { it(carrier) }
    }

    /**
     * Snapshot of the targets this plugin **actually registered** with KGP so far (issue #52) —
     * `selection ∩ supported` as of the last [supports] call, minus anything registration skipped.
     * One call instead of intersecting [resolvedSelection] and [resolvedSupported] by hand, but
     * deliberately *not* the same thing: this mirrors real registrations, so it is empty when KGP
     * is not applied, and omits `androidTarget` when no Android Gradle plugin is present (the #51
     * skip). For the abstract plan regardless of what registered, intersect the two resolved sets
     * yourself.
     *
     * It is a snapshot: a later [supports] call can union in more leaves. Read it *after* the
     * module's `supports { }` for simple cases, or use [onRegistered] for ordering-immune wiring.
     */
    public fun registered(): KmpTargetSet =
        KmpTargetSet.of(*registeredLog.map { it.leaf }.toTypedArray())

    /**
     * [registered] narrowed to [slice] via plain set algebra — family slicing without KGP konan
     * types: `registered(KmpTargetSet.appleMobile)` is exactly the registered iOS leaves.
     */
    public fun registered(slice: KmpTargetSet): KmpTargetSet = registered() intersect slice

    /**
     * Runs [action] for every target this plugin registered: immediately for the ones already
     * registered (replay), and again for each later registration (a second `supports { }` call
     * fires only its delta) — `configureEach` semantics, so convention plugins don't care whether
     * they run before or after the module's `supports { }`. The canonical per-target wiring,
     * KSP-style, without importing KGP konan types or re-deriving name mangling:
     * ```kotlin
     * // Before:
     * kotlin.targets.withType<KotlinNativeTarget>()
     *     .matching { it.konanTarget.family == Family.IOS }
     *     .forEach { add("ksp${it.targetName.replaceFirstChar(Char::titlecase)}", dep) }
     *
     * // After:
     * kmpTargets.onRegistered { add("ksp${it.gradleNameCapitalized}", dep) }
     * ```
     *
     * [RegisteredTarget.gradleName] is what registration actually called — `"desktop"` for a jvm
     * leaf renamed via [targetName], not a name re-derived from the leaf. Never fires when KGP is
     * absent (nothing registers), and never for the #51-skipped `androidTarget`.
     *
     * [action] runs at configuration time only — mirror the [supports] discipline and never hold it
     * (or the extension) from a task action; the [RegisteredTarget] it receives is itself
     * configuration-cache safe (a `data object` leaf plus strings).
     */
    public fun onRegistered(action: (RegisteredTarget) -> Unit) {
        registeredLog.toList().forEach(action)
        onRegisteredActions += action
    }

    /**
     * Runs [action] against the **live KGP [KotlinNativeTarget]** of every registered Apple leaf —
     * the no-mirror foundation for per-target Apple wiring. It is [onRegistered] routed one rung
     * closer to the metal: same replay-then-subscribe, ordering-immune guarantees (a `framework`
     * declared before or after `supports {}` behaves identically), but the receiver is the real
     * target, so frameworks, cinterops, linker options, and binary options are plain KGP you
     * already know — nothing this plugin has to re-declare and review on each Kotlin release:
     * ```kotlin
     * kmpTargets {
     *     supports { appleMobile }
     *     onAppleTarget {                       // this: KotlinNativeTarget
     *         binaries.framework("KotlinShared") { isStatic = false }
     *     }
     * }
     * ```
     *
     * Apple-ness comes from the model leaf ([KmpTarget.Native.Apple]), so no konan `Family`
     * introspection leaks in. Fires only when KGP is applied (otherwise nothing registers); runs at
     * configuration time only — mirror the [onRegistered] discipline and never hold the target (or
     * the extension) from a task action.
     */
    public fun onAppleTarget(action: KotlinNativeTarget.() -> Unit): Unit =
        onAppleNativeTarget(KmpTargetSet.apple, action)

    /**
     * Declares a Kotlin **Apple framework** once and attaches it to every registered Apple leaf in
     * [on] — replacing the hand-rolled "pick native targets, loop, `binaries.framework { … }`"
     * block (issue #105). Thin sugar over [onAppleTarget]: this plugin owns only the [baseName],
     * the creation-time [buildTypes], and the [on] filter (its own target algebra); the [configure]
     * body is the **real KGP [Framework]**, so `isStatic`, `export(…)`, `binaryOption(…)`, and
     * every future framework option come straight from KGP with no mirrored vocabulary:
     * ```kotlin
     * kmpTargets {
     *     supports { appleMobile }
     *     appleFramework("KotlinShared") {       // this: Framework
     *         isStatic = false
     *         export(libs.shared.core)           // KGP export(Any): catalog Provider / project / "g:a:v"
     *     }
     * }
     * ```
     *
     * [baseName] becomes the framework's `baseName`; KGP's `namePrefix` stays `""` so link-task
     * names (`linkDebugFrameworkIosArm64`) and the `embedAndSign…` contract match plain-KGP
     * conventions byte-for-byte. [buildTypes] defaults to KGP's own `DEFAULT_BUILD_TYPES` (DEBUG +
     * RELEASE) — the plugin never silently narrows a KGP default. Exports are realized lazily by
     * KGP at attach time (a version-catalog `Provider` is not `.get()`-ed at declaration). The
     * [configure] block runs after the `baseName` is set, so it always wins.
     *
     * Build-logic friendly: [on] is a raw [KmpTargetSet] (mirroring `supports(value)`), so
     * convention plugins declare frameworks without the type-safe DSL receiver. One framework per
     * module in v1 — a second call, a blank [baseName], or an [on] not ⊆ apple fails fast with the
     * offending ids.
     */
    public fun appleFramework(
        baseName: String,
        on: KmpTargetSet = KmpTargetSet.apple,
        buildTypes: Collection<NativeBuildType> = NativeBuildType.DEFAULT_BUILD_TYPES,
        configure: Framework.() -> Unit = {},
    ) {
        if (baseName.isBlank()) {
            throw GradleException("kmp-targets: appleFramework baseName must not be blank.")
        }
        val nonApple = (on - KmpTargetSet.apple).members
        if (nonApple.isNotEmpty()) {
            throw GradleException(
                "kmp-targets: appleFramework `on` must be a subset of apple targets. " +
                    "Offending: ${nonApple.map { it.id }.sorted()}."
            )
        }
        if (appleFrameworkDeclared) {
            throw GradleException(
                "kmp-targets: appleFramework can be declared at most once per module (v1) — KGP " +
                    "binary names collide on the empty namePrefix. Multiple frameworks need a " +
                    "namePrefix strategy (a follow-up)."
            )
        }
        appleFrameworkDeclared = true
        onAppleNativeTarget(on) {
            binaries.framework(buildTypes = buildTypes) {
                this.baseName = baseName
                configure()
            }
        }
    }

    /**
     * Shared resolve path for [onAppleTarget] and [appleFramework]: replays + subscribes via
     * [onRegistered], keeps only Apple leaves inside [within], and resolves each to its live KGP
     * [KotlinNativeTarget] through [kotlinTargetByName] before invoking [block]. The null-safe
     * chain means it is inert until the plugin installs the resolver under `withPlugin(KGP_ID)`.
     */
    private fun onAppleNativeTarget(within: KmpTargetSet, block: KotlinNativeTarget.() -> Unit) {
        onRegistered { registered ->
            if (registered.leaf is KmpTarget.Native.Apple && registered.leaf in within) {
                (kotlinTargetByName?.invoke(registered.gradleName) as? KotlinNativeTarget)?.block()
            }
        }
    }
}
