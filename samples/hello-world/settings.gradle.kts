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

// Heterogeneous modules, each declaring its supported shape via `kmpTargets { supported { … } }` in
// its build script (or leaving it unset for the default-all). The global KMP_TARGETS (see
// gradle.properties) is intersected against each module's set, then a minimal hierarchy template is
// applied. With KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64:
include(":shared-core") //    default-all  → jvm + iosMain (the 2 iOS leaves share iosMain; no appleMain)
include(":feature-mobile") // supported{mobile}     → iosMain only (jvm unsupported, Android unselected)
include(":jvm-tools") //      supported{jvm}        → jvm only, no intermediate source sets
include(":core-and-apple") // supported{apple+jvm}  → jvm + iosMain
include(":legacy-default") // default-all, opts OUT → KGP default: iosMain + appleMain + nativeMain
include(":plain-kmp") //      no kmp-targets at all → KGP default, untouched (non-interference)
include(":escape-hatch-dsl") // supported{jvm+linuxX64}  → jvm only (linuxX64 unselected, iOS unsupported)
