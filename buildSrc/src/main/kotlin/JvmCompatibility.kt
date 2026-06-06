import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * The oldest consumer this plugin promises to work with, expressed as compilation floors.
 *
 * A Gradle plugin's compiled output is consumed in two hostile-to-newness places:
 * 1. **`kotlin-dsl` build-logic compilation.** Convention plugins that wrap kmp-targets compile
 *    with the *Gradle-embedded* Kotlin compiler — Kotlin 2.0.x on the Gradle 8.11–8.14 line. That
 *    compiler must be able to read this jar's Kotlin metadata, so language/API levels are pinned
 *    to [KotlinVersion.KOTLIN_2_0].
 * 2. **The consumer's daemon JVM.** Builds routinely run on JDK 17 even when this repo develops on
 *    a newer JDK, so bytecode is pinned to 17 ([JvmTarget.JVM_17] / [JavaVersion.VERSION_17]).
 *
 * Strategy: build with the locally installed JDK (mise-pinned), explicitly target the floors — no
 * Gradle toolchains. See:
 * - https://jakewharton.com/gradle-toolchains-are-rarely-a-good-idea/
 * - https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
 */
internal val javaFloor = JavaVersion.VERSION_17
internal val jvmTargetFloor = JvmTarget.JVM_17
internal val kotlinFloor = KotlinVersion.KOTLIN_2_0

/**
 * The stdlib version published as this plugin's dependency. Without this pin, KGP adds the
 * *toolchain's* stdlib (2.3.x) to the POM and consumers on the floor pull 2.3-metadata jars onto
 * their `kotlin-dsl` compile classpath — failing exactly the way the language/api pin prevents.
 * Must stay on the same minor as [kotlinFloor]. Applied via `kotlin { coreLibrariesVersion }` in
 * the consuming build script — `KotlinProjectExtension` lives in the full KGP jar, which buildSrc
 * deliberately keeps off its classpath (see buildSrc/build.gradle.kts).
 */
const val KOTLIN_FLOOR_STDLIB: String = "2.0.21"

/**
 * Pins this project's compiled output to the consumer floors documented above.
 *
 * - Java: `sourceCompatibility`/`targetCompatibility` plus `--release`, so javac refuses JDK-18+
 *   APIs instead of producing bytecode that fails at runtime on a 17 daemon.
 * - Kotlin: `jvmTarget` for bytecode, `languageVersion`/`apiVersion` for metadata the embedded
 *   2.0.x compiler can read, and `-Xjdk-release` so kotlinc gets the same accidental-new-API
 *   protection javac gets from `--release`.
 *
 * That these floors hold in the built jar is enforced by [registerCompatFloorsVerification].
 */
fun Project.configureJvmCompatibility() {
    extensions.configure(JavaPluginExtension::class.java) {
        sourceCompatibility = javaFloor
        targetCompatibility = javaFloor
    }

    tasks.withType(JavaCompile::class.java).configureEach {
        options.release.set(javaFloor.majorVersion.toInt())
    }

    tasks.withType(KotlinCompilationTask::class.java).configureEach {
        compilerOptions.languageVersion.set(kotlinFloor)
        compilerOptions.apiVersion.set(kotlinFloor)
        (compilerOptions as? KotlinJvmCompilerOptions)?.let { options ->
            options.jvmTarget.set(jvmTargetFloor)
            options.freeCompilerArgs.add("-Xjdk-release=${jvmTargetFloor.target}")
        }
    }
}
