plugins {
    // Supports JVM only. With kmptargets.targets=jvm,iosArm64,iosSimulatorArm64 the iOS targets are
    // dropped
    // and only `jvm` registers.
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

kmpTargets { supports { jvm } }
