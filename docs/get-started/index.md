# Installation

## Prerequisites

| Requirement | Floor | Notes |
|---|---|---|
| Gradle | **8.11+** | |
| Daemon JVM | **JDK 17** | |
| Kotlin Gradle Plugin | **2.2+** | apply `kotlin("multiplatform")` yourself — the plugin never applies KGP for you |

How the floors are enforced: [Compatibility](../compatibility.md).

## Apply the plugin

The plugin is published to **Maven Central** (group `com.rsicarelli`, artifact `kmp-targets-gradle-plugin`, plugin id `com.rsicarelli.kmptargets`). Make sure `mavenCentral()` is in your plugin repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then apply it next to KGP in each module:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0"
}

kmpTargets {
    supports { mobile }
}
```

!!! tip "Version catalog"
    With a catalog entry the version lives in one place:

    ```toml
    # gradle/libs.versions.toml
    [versions]
    kmpTargets = "0.1.0"

    [plugins]
    kmpTargets = { id = "com.rsicarelli.kmptargets", version.ref = "kmpTargets" }
    ```

    ```kotlin
    plugins {
        kotlin("multiplatform")
        alias(libs.plugins.kmpTargets)
    }
    ```

Build-logic that references the DSL types directly depends on the artifact:

```kotlin
// build-logic/build.gradle.kts
dependencies {
    implementation("com.rsicarelli:kmp-targets-gradle-plugin:0.1.0")
}
```

!!! tip "KDoc on hover (IntelliJ)"
    The plugin ships a sources jar, but IntelliJ does not attach sources for build-script dependencies by default. Enable **Settings → Advanced Settings → Build Tools. Gradle → "Attach scripts dependencies sources"** (plus **"Download sources"**) and re-sync — hovering `supports`, `mobile`, `iosArm64`, … then shows their documentation.

## Consuming snapshots

Every push to `main` publishes a `-SNAPSHOT` to the Central Portal snapshots repository. To ride the bleeding edge, add the snapshots repo to **both** plugin and dependency resolution:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
```

!!! warning "Snapshot hygiene"
    Gradle caches changing modules for 24h by default. After a new snapshot lands, refresh explicitly:

    ```bash
    ./gradlew build --refresh-dependencies
    ```

    If resolution fails with a bare `Could not find com.rsicarelli:kmp-targets-gradle-plugin:0.1.0 the repository list is the first thing to check — snapshots only exist in the snapshots repo, releases only on Maven Central.

## Contributing / local development

Working on the plugin itself? The repo publishes to `mavenLocal()` for the sample dev loop:

```bash
task publish-local           # publishes the plugin (and marker) to ~/.m2
task sample                  # smoke test against samples/hello-world
```

This is a contributor workflow — consumers should use Maven Central or the snapshots repo above.

---

**Next: [Quick Start →](quick-start.md)**
