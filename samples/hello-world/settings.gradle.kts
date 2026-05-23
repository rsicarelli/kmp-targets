pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}

rootProject.name = "hello-world"

// Heterogeneous modules, each declaring its supported shape by the convention plugin id it applies.
// The global KMP_TARGETS (see gradle.properties) is intersected against each module's set, then a
// minimal hierarchy template is applied. With KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64:
include(":shared-core") //    .library → jvm + iosMain (the 2 iOS leaves share iosMain; no appleMain)
include(":feature-mobile") // .mobile  → iosMain only (jvm unsupported here, Android unselected)
include(":jvm-tools") //      .jvm     → jvm only, no intermediate source sets
include(":core-and-apple") // .apple + .jvm (composition) → jvm + iosMain
include(":legacy-default") // .library but opts OUT → KGP default: iosMain + appleMain + nativeMain
include(":plain-kmp") //      no kmp-targets at all → KGP default, untouched (non-interference)
