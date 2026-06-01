pluginManagement {
    // The sample's own convention plugin lives in an included build so its `kmptargets.module` id
    // resolves during plugin resolution (used by :eager-conventions).
    includeBuild("build-logic")
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

// Heterogeneous modules, each declaring its supported shape via `kmpTargets { supports { … } }` in
// its build script. Targets are explicit — a module with no `supports` registers nothing. The
// global
// KMP_TARGETS (see gradle.properties) is intersected against each module's set, then a minimal
// hierarchy template is applied. With KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64:

// supports{all} → jvm + iosMain (the 2 iOS leaves share iosMain; no appleMain)
include(":shared-core")

// supports{mobile} → iosMain only (jvm unsupported here, Android unselected)
include(":feature-mobile")

// supports{jvm} → jvm only, no intermediate source sets
include(":jvm-tools")

// supports{apple+jvm} → jvm + iosMain
include(":core-and-apple")

// supports{all}, opts OUT → KGP default: iosMain + appleMain + nativeMain
include(":legacy-default")

// no kmp-targets at all → KGP default, untouched (non-interference)
include(":plain-kmp")

// supports{jvm+linuxX64} → jvm only (linuxX64 unselected, iOS unsupported)
include(":escape-hatch-dsl")

// build-logic convention (`kmptargets.module`) that declares `supports` then reads `kotlin.targets`
// synchronously — proves eager registration. `verifyEagerTargets` fails the build if it regresses.
include(":eager-conventions")
