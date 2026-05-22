# kmp-targets

> Dynamically select which Kotlin Multiplatform targets to build.

**Status:** bootstrap (pre-release) — the foundation is here; the selector logic ships in the next release.

`kmp-targets` is a Gradle plugin for Kotlin Multiplatform projects that lets each developer (and each CI runner) choose which KMP targets to build, via a single Gradle property:

```properties
# local.properties or -P
KMP_TARGETS=android,iosArm64
```

Targets you don't select are never registered with KGP, so their compile/link/KSP/publish tasks never run — cutting Gradle sync and build times dramatically when you only need a subset.

## Intended usage (not yet implemented)

```kotlin
// build.gradle.kts
plugins {
    id("com.rsicarelli.kmptargets") version "<version>"
    kotlin("multiplatform")
}
```

```properties
# Set in local.properties, as -P flag, or as env var
KMP_TARGETS=android              # → android + jvm (pairing rule)
KMP_TARGETS=iosAll               # → all iOS targets (sugar)
KMP_TARGETS=jvm,iosArm64         # → JVM + iOS device only
# (absent / blank)               # → all targets (default, no behavior change)
```

## Installation

Coming soon — see **Status** above. The plugin is not yet published to the Gradle Plugin Portal or Maven Central. Track progress in the issues / releases.

## Development

Requirements: [mise](https://mise.jdx.dev) (pins JDK 23) and [Task](https://taskfile.dev) (developer task runner).

```bash
mise install        # provisions Temurin 23.0.2+7
task ci             # build + test + sample
```

See [CONTRIBUTING.md](./CONTRIBUTING.md) for the full development guide.

## License

Apache-2.0. See [LICENSE](./LICENSE).
