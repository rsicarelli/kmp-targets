plugins {
    // Apply KGP + the base kmp-targets id, then declare `supports { all }` to build everything the
    // global KMP_TARGETS selects. With KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64 this registers
    // those three; the minimal template shares the two iOS leaves under a single `iosMain` (no
    // redundant `appleMain`/`nativeMain`). Compare with :legacy-default, which opts out and keeps
    // both.
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

// Targets are explicit: with no `supports` nothing would register. `supports { all }` is the opt-in
// to "build whatever KMP_TARGETS selects".
kmpTargets { supports { all } }
