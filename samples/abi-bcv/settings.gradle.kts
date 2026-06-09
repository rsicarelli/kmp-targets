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

    // Share the main repo's version catalog so Kotlin/ktfmt/kmp-targets versions stay in lockstep
    // with the plugin under test. The `0.1.0-SNAPSHOT` of kmp-targets a consumer would type stays
    // visible verbatim in build.gradle.kts.
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}

rootProject.name = "abi-bcv"
