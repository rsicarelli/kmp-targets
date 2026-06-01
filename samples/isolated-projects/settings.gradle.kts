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

rootProject.name = "isolated-projects"

// Two modules wired via the DSL, built with Gradle Isolated Projects enabled (see
// gradle.properties). Isolated Projects isolates each project's configuration and forbids
// cross-project access (and `Project` capture). This sample therefore only configures cleanly while
// the plugin keeps touching nothing but the project it is applied to — making it the executable
// guard for that contract.
include(":lib") // default-all → supports every target; with KMP_TARGETS=jvm registers jvm only
include(":app") // supported{jvm} → registers jvm; depends on :lib through a project dependency
