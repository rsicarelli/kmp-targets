import com.rsicarelli.kmptargets.KmpTargetsDsl

plugins {
    // Per-module JVM target rename (issue #49): codebases that grew up before the default-hierarchy
    // era register their JVM target as `jvm("desktop")` and carry `src/desktopMain` dirs,
    // `-desktop`
    // artifact suffixes, and CI task names (`compileKotlinDesktop`) keyed off that name. The rename
    // keeps all of those stable while the selection token stays `jvm`.
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
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
