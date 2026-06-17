pluginManagement {
    repositories {
        // The plugin under test comes from ~/.m2/repository after `task publish-local`.
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

    // Share the main repo's version catalog so Kotlin/kmp-targets versions stay in lockstep with
    // the
    // plugin under test; the `0.1.0-SNAPSHOT` a consumer would type stays visible in
    // build.gradle.kts.
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}

rootProject.name = "xcode-env"
