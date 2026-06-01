// Included build that contributes the sample's own `kmptargets.module` convention plugin. Kept
// separate from the consuming modules so the convention is compiled and published as a real plugin,
// the way a team's `build-logic` would be.
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
    // Share the main repo's catalog so the Kotlin version matches the plugin under test.
    versionCatalogs { create("libs") { from(files("../../../gradle/libs.versions.toml")) } }
}

rootProject.name = "build-logic"
