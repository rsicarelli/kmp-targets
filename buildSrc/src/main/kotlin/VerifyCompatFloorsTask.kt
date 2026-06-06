import java.util.jar.JarFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

/**
 * Guards the consumer floors declared in JvmCompatibility.kt by inspecting the *built jar* — the
 * artifact consumers actually load — instead of trusting compiler flags to have applied:
 * - every class file's major version must be ≤ [maxClassMajorVersion] (61 = Java 17), or JDK 17
 *   daemons throw `UnsupportedClassVersionError`;
 * - every `@kotlin.Metadata` `mv` must match [expectedMetadataMajorMinor] ([2, 0]), or the
 *   Gradle-embedded Kotlin 2.0.x compiler rejects the jar from `kotlin-dsl` build-logic;
 * - the resolved `kotlin-stdlib` must be [expectedStdlibVersion], or the POM ships a
 *   toolchain-version stdlib that drags 2.3 metadata back onto the consumer's compile classpath.
 *
 * Wired into `check` by [registerCompatFloorsVerification], so `task ci` fails on any regression.
 */
abstract class VerifyCompatFloorsTask : DefaultTask() {

    @get:InputFile abstract val pluginJar: RegularFileProperty

    @get:Input abstract val maxClassMajorVersion: Property<Int>

    @get:Input abstract val expectedMetadataMajorMinor: ListProperty<Int>

    @get:Input abstract val expectedStdlibVersion: Property<String>

    @get:Input abstract val resolvedStdlibVersion: Property<String>

    @TaskAction
    fun verify() {
        val maxMajor = maxClassMajorVersion.get()
        val expectedMv = expectedMetadataMajorMinor.get()
        val violations = mutableListOf<String>()

        JarFile(pluginJar.get().asFile).use { jar ->
            jar.entries()
                .asSequence()
                .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                .forEach { entry ->
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }

                    // Class-file major version: u2 at offset 6 (magic u4, minor u2, major u2).
                    val major = ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
                    if (major > maxMajor) {
                        violations +=
                            "${entry.name}: class file major version $major > $maxMajor " +
                                "(Java ${maxMajor - 44})"
                    }

                    val mv = readKotlinMetadataVersion(bytes)
                    if (mv != null && (mv[0] != expectedMv[0] || mv[1] != expectedMv[1])) {
                        violations +=
                            "${entry.name}: @kotlin.Metadata mv=${mv.toList()}, expected " +
                                "[${expectedMv[0]}, ${expectedMv[1]}, *] — unreadable by the " +
                                "Gradle-embedded Kotlin ${expectedMv[0]}.${expectedMv[1]} compiler"
                    }
                }
        }

        if (resolvedStdlibVersion.get() != expectedStdlibVersion.get()) {
            violations +=
                "kotlin-stdlib resolves to ${resolvedStdlibVersion.get()}, expected " +
                    "${expectedStdlibVersion.get()} — the POM would carry a stdlib above the " +
                    "consumer floor (check `kotlin { coreLibrariesVersion }`)"
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Plugin jar violates the consumer compatibility floors " +
                    "(see buildSrc/src/main/kotlin/JvmCompatibility.kt):\n" +
                    violations.joinToString("\n") { "  - $it" }
            )
        }
    }

    /** Extracts the `mv` array from `@kotlin.Metadata`, or null for non-Kotlin classes. */
    private fun readKotlinMetadataVersion(classBytes: ByteArray): IntArray? {
        var mv: IntArray? = null
        val collector =
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitAnnotation(
                    descriptor: String,
                    visible: Boolean,
                ): AnnotationVisitor? {
                    if (descriptor != "Lkotlin/Metadata;") return null
                    return object : AnnotationVisitor(Opcodes.ASM9) {
                        override fun visitArray(name: String?): AnnotationVisitor? {
                            if (name != "mv") return null
                            val values = mutableListOf<Int>()
                            return object : AnnotationVisitor(Opcodes.ASM9) {
                                override fun visit(name: String?, value: Any?) {
                                    values += value as Int
                                }

                                override fun visitEnd() {
                                    mv = values.toIntArray()
                                }
                            }
                        }
                    }
                }
            }
        val flags = ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
        ClassReader(classBytes).accept(collector, flags)
        return mv
    }
}

/**
 * Registers `verifyCompatFloors` against this project's `jar` output and hooks it into `check`.
 * All inputs are primitives/Providers resolved at configuration time — no `Project` capture, so
 * the task is configuration-cache safe.
 */
fun Project.registerCompatFloorsVerification() {
    // The stdlib version the consumer will actually resolve (and the POM will declare). Lazy:
    // resolution happens only when the task's inputs are fingerprinted, never at configuration.
    val resolvedStdlib =
        configurations
            .getByName("runtimeClasspath")
            .incoming
            .resolutionResult
            .rootComponent
            .map { root -> findKotlinStdlibVersion(root) ?: "absent" }

    val verifyCompatFloors =
        tasks.register("verifyCompatFloors", VerifyCompatFloorsTask::class.java) {
            pluginJar.set(tasks.named("jar", Jar::class.java).flatMap { it.archiveFile })
            // Class-file major = Java feature version + 44 → Java 17 = 61.
            maxClassMajorVersion.set(javaFloor.majorVersion.toInt() + 44)
            expectedMetadataMajorMinor.set(kotlinFloor.version.split(".").map(String::toInt))
            expectedStdlibVersion.set(KOTLIN_FLOOR_STDLIB)
            resolvedStdlibVersion.set(resolvedStdlib)
        }

    tasks.named("check") { dependsOn(verifyCompatFloors) }
}

private fun findKotlinStdlibVersion(root: ResolvedComponentResult): String? {
    val seen = mutableSetOf<ResolvedComponentResult>()
    val queue = ArrayDeque(listOf(root))
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (!seen.add(component)) continue
        for (dependency in component.dependencies.filterIsInstance<ResolvedDependencyResult>()) {
            val module = dependency.selected.moduleVersion
            if (module?.group == "org.jetbrains.kotlin" && module.name == "kotlin-stdlib") {
                return module.version
            }
            queue += dependency.selected
        }
    }
    return null
}
