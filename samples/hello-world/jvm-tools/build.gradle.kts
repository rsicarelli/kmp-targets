plugins {
    // Supports JVM only. With kmptargets.targets=jvm,iosArm64,iosSimulatorArm64 the iOS targets are
    // dropped
    // and only `jvm` registers.
    kotlin("multiplatform")
    alias(libs.plugins.kmpTargets)
}

kmpTargets { supports { jvm } }
