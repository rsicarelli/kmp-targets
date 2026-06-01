plugins {
    // Escape hatch: a one-off module that fits no preset. Apply KGP + the base kmp-targets id
    // yourself, then declare the supported set in the build script with the type-safe DSL — the
    // whole target vocabulary is in scope as set algebra. (KGP version comes from the root build's
    // `apply false` declaration, so it isn't repeated here.)
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

kmpTargets {
    // Supports JVM + Linux only. With the sample's KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64,
    // selection ∩ supported = jvm — so only `jvm` registers (linuxX64 is supported but unselected;
    // the iOS leaves are selected but unsupported). This declaration is read from the build-script
    // body and honored by the deferred registration pass — the old "timing wall" used to drop it.
    supports { jvm + linuxX64 }
}
