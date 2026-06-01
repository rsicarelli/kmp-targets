import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Sample convention plugin (`kmptargets.module`): wraps the kmp-targets base plugin so a consuming
 * module applies one id and uses the `kmpModule { … }` DSL.
 *
 * Implemented as a binary plugin (not a precompiled `.gradle.kts`) so it is only ever applied to a
 * real consuming project — where KGP is present via the consumer's own `kotlin("multiplatform")`.
 * That keeps a single shared KGP classloader across the sample; this build never bundles its own KGP
 * (it depends on KGP `compileOnly`, for types only).
 */
class KmpModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.rsicarelli.kmptargets")

        // The ordering-immune counterpart to `kmpModule`'s eager read: react to each target as it is
        // registered. Fires when the consumer's `supports { … }` runs, regardless of order.
        val log = project.logger
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.extensions.getByType<KotlinMultiplatformExtension>().targets.configureEach {
                log.lifecycle("reactively configured target: $name")
            }
        }
    }
}
