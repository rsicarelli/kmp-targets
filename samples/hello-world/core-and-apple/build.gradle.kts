plugins {
    // Composition: supported = apple ∪ jvm. With KMP_TARGETS=jvm,iosSimulatorArm64 this registers
    // BOTH jvm and iosSimulatorArm64 — more than .apple alone (iOS only) or .jvm alone (jvm only),
    // proving that multiple convention ids union their supported sets.
    id("com.rsicarelli.kmptargets.apple") version "0.1.0-SNAPSHOT"
    id("com.rsicarelli.kmptargets.jvm") version "0.1.0-SNAPSHOT"
}
