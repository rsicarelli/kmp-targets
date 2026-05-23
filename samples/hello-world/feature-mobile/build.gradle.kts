plugins {
    // Supports Android + iOS only. With KMP_TARGETS=jvm,iosSimulatorArm64 the jvm is dropped
    // (not supported here) and only iosSimulatorArm64 registers — Android isn't selected, so no AGP.
    id("com.rsicarelli.kmptargets.mobile") version "0.1.0-SNAPSHOT"
}
