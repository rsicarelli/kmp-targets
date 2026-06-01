plugins {
    // Apply KGP + the base kmp-targets id; the supported set defaults to `all` when no
    // `kmpTargets { supports { … } }` block is declared. With
    // KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64
    // this registers those three; the minimal template shares the two iOS leaves under a single
    // `iosMain` (no redundant `appleMain`/`nativeMain`). Compare with :legacy-default, which opts
    // out
    // and keeps both.
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}
