import com.rsicarelli.kmptargets.KmpTargetsDsl

plugins {
    // Per-module JVM target rename (issue #49): codebases that grew up before the default-hierarchy
    // era register their JVM target as `jvm("desktop")` and carry `src/desktopMain` dirs,
    // `-desktop`
    // artifact suffixes, and CI task names (`compileKotlinDesktop`) keyed off that name. The rename
    // keeps all of those stable while the selection token stays `jvm`.
    kotlin("multiplatform")
    alias(libs.plugins.kmpTargets)
}

kmpTargets {
    // Must come BEFORE supports { } — registration is eager and one-way.
    targetName(KmpTargetsDsl.jvm, "desktop")
    supports { jvm + iosArm64 }
}

// Regression gate, wired into `check` and run by `task ci`. Supports exactly {jvm, iosArm64} for
// the same reason as :eager-conventions: every sample-matrix CI job's selection is contractually
// required to include BOTH tokens (see .github/workflows/sample-matrix.yml), so the intersection —
// and this expected list — is identical on every host and under the default selection. The jvm
// leaf must register under its custom name, and ONLY that name. Captures a List<String> at
// configuration time (config-cache safe).
val observedTargets = kotlin.targets.names.filter { it != "metadata" }.sorted()

tasks.register("verifyRenamedTargets") {
    val observed = observedTargets
    val expected = listOf("desktop", "iosArm64")
    doLast {
        check(observed == expected) {
            "jvm target rename regressed: expected $expected at configuration time, saw $observed"
        }
    }
}

tasks.named("check") { dependsOn("verifyRenamedTargets") }

// Umbrella rename gate (issue #77): kmpCompileAll / kmpTestAll must wire the RENAMED tasks
// (`compileKotlinDesktop` / `desktopTest`), never the default `compileKotlinJvm` / `jvmTest` a
// hardcoded CI list would have used — the latter silently match zero tasks here. iosArm64 is a
// device-only native, so it contributes a compile task but no test task. Dependency names are
// captured as List<String> at configuration time (config-cache safe — no Task escapes into doLast).
fun umbrellaDeps(name: String): List<String> =
    tasks.named(name).get().let { task ->
        task.taskDependencies.getDependencies(task).map { it.name }.sorted()
    }

val compileAllDeps = umbrellaDeps("kmpCompileAll")
val testAllDeps = umbrellaDeps("kmpTestAll")

tasks.register("verifyUmbrellaWiring") {
    val compileDeps = compileAllDeps
    val testDeps = testAllDeps
    doLast {
        check("compileKotlinDesktop" in compileDeps && "compileKotlinJvm" !in compileDeps) {
            "kmpCompileAll rename regressed: expected compileKotlinDesktop (not compileKotlinJvm), " +
                "saw $compileDeps"
        }
        check("desktopTest" in testDeps && "jvmTest" !in testDeps) {
            "kmpTestAll rename regressed: expected desktopTest (not jvmTest), saw $testDeps"
        }
    }
}

tasks.named("check") { dependsOn("verifyUmbrellaWiring") }
