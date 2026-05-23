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
// The global KMP_TARGETS (see gradle.properties) is intersected against each module's set.
include(":shared-core") //    .library → all targets
include(":feature-mobile") // .mobile  → Android + iOS
include(":jvm-tools") //      .jvm     → JVM only
include(":core-and-apple") // .apple + .jvm (composition) → iOS/macOS ∪ JVM
