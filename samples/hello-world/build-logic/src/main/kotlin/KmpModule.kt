import com.rsicarelli.kmptargets.KmpTargetsDsl
import com.rsicarelli.kmptargets.KmpTargetsExtension
import com.rsicarelli.kmptargets.model.KmpTargetSet
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Declares this module's supported set, then reads `kotlin.targets` **synchronously** and hands the
 * registered target names to [perTarget].
 *
 * This is the capability that removing the after-evaluate pass unlocks: because `supports`
 * registers eagerly, the targets exist the instant this function reads them. Under the old deferred
 * model the same read returned an empty set, which is why convention plugins that wired per-target
 * behaviour right after declaration were fragile. The reactive `targets.configureEach { … }` in the
 * `kmptargets.module` plugin is the ordering-immune alternative shown alongside it.
 */
fun Project.kmpModule(
    supported: KmpTargetsDsl.() -> KmpTargetSet,
    perTarget: (List<String>) -> Unit = {},
) {
    extensions.getByType<KmpTargetsExtension>().supports(supported)
    val targets =
        extensions.getByType<KotlinMultiplatformExtension>().targets.names.filter {
            it != "metadata"
        }
    perTarget(targets)
}
