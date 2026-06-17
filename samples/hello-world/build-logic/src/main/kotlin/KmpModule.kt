import com.rsicarelli.kmptargets.KmpTargetsDsl
import com.rsicarelli.kmptargets.KmpTargetsExtension
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.model.RegisteredTarget
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework

/**
 * Declares this module's supported set and hands every registered target to [perTarget] via
 * [KmpTargetsExtension.onRegistered] — the plugin's query surface (issue #52), the canonical way
 * to wire per-target things (KSP configurations, per-target tasks) without touching KGP types.
 *
 * `onRegistered` has `configureEach` semantics: hooked before `supports`, it fires once per leaf
 * as registration happens; hooked after, it replays — so this convention never cares about
 * ordering, and a later `supports { }` union fires only its delta. Each [RegisteredTarget] carries
 * the Gradle name registration actually used (a jvm leaf renamed via `targetName` reports the
 * custom name), pre-mangled as `gradleNameCapitalized` for configuration-name wiring.
 *
 * The pre-#52 version of this helper read `kotlin.targets.names` synchronously right after
 * `supports` — possible because registration is eager, but it left every convention re-deriving
 * name filtering itself (and KSP-style family slicing meant importing KGP konan internals).
 *
 * When [appleFramework] is non-null, the helper also declares an Apple framework of that base name
 * via [KmpTargetsExtension.appleFramework], narrowed by the raw [on] set ([KmpTargetSet], not the
 * type-safe receiver — the build-logic-friendly overload). [framework] configures the **real KGP
 * [Framework]**, so a convention can set `isStatic`/`export(…)` without this build re-rolling the
 * per-target loop or mirroring KGP's framework vocabulary. [perTarget] stays the trailing parameter
 * so existing `kmpModule(supported = …) { … }` call sites are unaffected.
 */
fun Project.kmpModule(
    supported: KmpTargetsDsl.() -> KmpTargetSet,
    appleFramework: String? = null,
    on: KmpTargetSet = KmpTargetSet.apple,
    framework: Framework.() -> Unit = {},
    perTarget: (RegisteredTarget) -> Unit = {},
) {
    val kmpTargets = extensions.getByType<KmpTargetsExtension>()
    kmpTargets.onRegistered(perTarget)
    if (appleFramework != null) {
        kmpTargets.appleFramework(appleFramework, on = on, configure = framework)
    }
    kmpTargets.supports(supported)
}
