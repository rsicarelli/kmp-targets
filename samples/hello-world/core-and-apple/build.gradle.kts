plugins {
    // Composition: supported = apple ∪ jvm. With KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64 this
    // registers jvm + both iOS leaves — more than `apple` alone (all Apple platforms) or `jvm`
    // alone
    // (jvm only). The DSL takes the place of stacking two convention ids: the supported set is just
    // set algebra in the block. The template yields jvm at common + a single `iosMain` (no
    // `appleMain`/`nativeMain`).
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

kmpTargets { supports { apple + jvm } }
