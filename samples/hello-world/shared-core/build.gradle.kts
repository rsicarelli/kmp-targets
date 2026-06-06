plugins {
    // Apply KGP + the base kmp-targets id, then declare `supports { all }` to build everything the
    // global kmptargets.targets selects. With kmptargets.targets=jvm,iosArm64,iosSimulatorArm64
    // this registers
    // those three; the minimal template shares the two iOS leaves under a single `iosMain` (no
    // redundant `appleMain`/`nativeMain`). Compare with :legacy-default, which opts out and keeps
    // both.
    kotlin("multiplatform")
    alias(libs.plugins.kmpTargets)
}

// Targets are explicit: with no `supports` nothing would register. `supports { all }` is the opt-in
// to "build whatever kmptargets.targets selects".
kmpTargets { supports { all } }
