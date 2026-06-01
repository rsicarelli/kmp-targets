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

    // Share the main repo's version catalog so the Kotlin and ktfmt versions stay in lock-step with
    // the plugin under test. The `0.1.0-SNAPSHOT` of kmp-targets remains hardcoded in each sample's
    // `build.gradle.kts` — that's the version a consumer would type, so the sample shows it
    // verbatim.
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}

rootProject.name = "hello-world"

// Heterogeneous modules, each declaring its supported shape via `kmpTargets { supported { … } }` in
// its build script (or leaving it unset for the default-all). The global KMP_TARGETS (see
// gradle.properties) is intersected against each module's set, then a minimal hierarchy template is
// applied. With KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64:

// default-all → jvm + iosMain (the 2 iOS leaves share iosMain; no appleMain)
include(":shared-core")

// supported{mobile} → iosMain only (jvm unsupported here, Android unselected)
include(":feature-mobile")

// supported{jvm} → jvm only, no intermediate source sets
include(":jvm-tools")

// supported{apple+jvm} → jvm + iosMain
include(":core-and-apple")

// default-all, opts OUT → KGP default: iosMain + appleMain + nativeMain
include(":legacy-default")

// no kmp-targets at all → KGP default, untouched (non-interference)
include(":plain-kmp")

// supported{jvm+linuxX64} → jvm only (linuxX64 unselected, iOS unsupported)
include(":escape-hatch-dsl")
