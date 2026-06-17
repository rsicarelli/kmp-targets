plugins {
    // Composition: supported = apple ∪ jvm. With kmptargets.targets=jvm,iosArm64,iosSimulatorArm64
    // this
    // registers jvm + both iOS leaves — more than `apple` alone (all Apple platforms) or `jvm`
    // alone
    // (jvm only). The DSL takes the place of stacking two convention ids: the supported set is just
    // set algebra in the block. The template yields jvm at common + a single `iosMain` (no
    // `appleMain`/`nativeMain`).
    kotlin("multiplatform")
    alias(libs.plugins.kmpTargets)
}

kmpTargets {
    supports { apple + jvm }

    // FRAMEWORK PROOF (.kts). Declare the iOS/macOS framework once; the plugin attaches it to every
    // registered Apple leaf via the existing onRegistered replay — no target loop, no ad-hoc
    // property picker. The configure block is the *real* KGP `Framework`, so `isStatic` and any
    // future framework option come straight from KGP with nothing mirrored in this plugin.
    // `xcframework = true` (opt-in) also assembles the registered slices into one `.xcframework`
    // (`assembleKotlinSharedXCFramework`); the config is created lazily, so a jvm-only lane wires
    // nothing. Assembly runs on a macOS host (xcodebuild); registration is host-blind.
    appleFramework("KotlinShared", xcframework = true) { isStatic = false }
}
