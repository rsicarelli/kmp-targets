plugins {
    // Escape hatch: a one-off module that fits no preset. Apply KGP + the base kmp-targets id
    // yourself, then declare the supported set in the build script with the type-safe DSL — the
    // whole target vocabulary is in scope as set algebra. (KGP version comes from the root build's
    // `apply false` declaration, so it isn't repeated here.)
    kotlin("multiplatform")
    alias(libs.plugins.kmpTargets)
}

kmpTargets {
    // Supports JVM + Linux only. With the sample's
    // kmptargets.targets=jvm,iosArm64,iosSimulatorArm64,
    // selection ∩ supported = jvm — so only `jvm` registers (linuxX64 is supported but unselected;
    // the iOS leaves are selected but unsupported). `supports` registers eagerly, the moment this
    // line runs.
    supports { jvm + linuxX64 }
}
