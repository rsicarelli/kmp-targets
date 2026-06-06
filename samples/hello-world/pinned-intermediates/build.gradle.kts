plugins {
    // The no-collapse mode (issue #50). With collapse ON (the default), this module's two selected
    // iOS leaves would share a single `iosMain` and — worse — narrowing the selection to ONE leaf
    // (`-Pkmptargets.targets=iosArm64`, the everyday "build for device only" move) would drop
    // `iosMain` entirely, breaking any module with load-bearing `src/iosMain` code. With collapse
    // OFF, every owning group materializes (≥1 present child): `iosMain`, `appleMain`, and
    // `nativeMain` all exist, and they survive a single-leaf selection. The empty parents are
    // inert. What REGISTERS never changes — only which intermediate source sets materialize.
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

kmpTargets {
    // Same ordering rule as hierarchyTemplate: set BEFORE `supports`, which eagerly registers and
    // applies the template. Project DSL wins over the global `kmptargets.hierarchyCollapse` key.
    collapseHierarchy.set(false)
    supports { appleMobile }
}

// Regression gate, wired into `check` and run by `task ci`. The read is a provider on purpose —
// unlike eager TARGET registration, the template's intermediate SOURCE SETS materialize during
// KGP's finalization, after this body runs. The provider realizes post-body and the config cache
// stores only the resulting Set<String> (no Project captured). Host-blind: asserts source-set
// existence only, no native compilation.
val observedSourceSets = provider { kotlin.sourceSets.names.toSet() }

tasks.register("verifyPinnedIntermediates") {
    val observed = observedSourceSets
    val expected = setOf("iosMain", "appleMain", "nativeMain")
    doLast {
        val sets = observed.get()
        check(sets.containsAll(expected)) {
            "no-collapse regressed: expected $expected to materialize, saw $sets"
        }
    }
}

tasks.named("check") { dependsOn("verifyPinnedIntermediates") }
