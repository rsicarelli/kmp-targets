import com.rsicarelli.kmptargets.KmpTargetsDsl
import com.rsicarelli.kmptargets.KmpTargetsExtension
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.model.RegisteredTarget
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

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
 */
fun Project.kmpModule(
    supported: KmpTargetsDsl.() -> KmpTargetSet,
    perTarget: (RegisteredTarget) -> Unit = {},
) {
    val kmpTargets = extensions.getByType<KmpTargetsExtension>()
    kmpTargets.onRegistered(perTarget)
    kmpTargets.supports(supported)
}
