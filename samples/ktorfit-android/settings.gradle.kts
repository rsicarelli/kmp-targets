pluginManagement {
    repositories {
        mavenLocal() // resolves the kmp-targets plugin published via `task publish-local`
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

    // The `libs` catalog is auto-created by Gradle from this sample's own `gradle/libs.versions.toml`
    // (Kotlin/AGP/KSP/ktorfit). Keeping it local avoids polluting the plugin's shared catalog; Kotlin
    // is pinned to the same 2.3.21 the plugin builds with.
}

rootProject.name = "ktorfit-android"

// The hypothesis under test: a ktorfit (KSP-based) API in a module that selects ANDROID ONLY via the
// kmpTargets DSL. The global selection (kmp-targets.properties) is `androidTarget,jvm`; each module's
// `supports { … }` narrows it.

// supports { androidTarget } → Android only. This is the case the user wants to validate.
include(":api-android-only")

// supports { androidTarget + jvm } → CONTROL. Same ktorfit API across two targets, proving whether
// any failure is specific to the single-target/Android-only selection rather than ktorfit in general.
include(":api-multiplatform")
