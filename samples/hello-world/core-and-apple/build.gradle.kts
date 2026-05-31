plugins {
    // Composition: supported = apple ∪ jvm. With KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64 this
    // registers jvm + both iOS leaves — more than .apple alone (all Apple platforms) or .jvm alone (jvm only),
    // proving that multiple convention ids union their supported sets. The template yields jvm at
    // common + a single `iosMain` (no `appleMain`/`nativeMain`).
    id("com.rsicarelli.kmptargets.apple") version "0.1.0-SNAPSHOT"
    id("com.rsicarelli.kmptargets.jvm") version "0.1.0-SNAPSHOT"
}
