# kmp-targets

> Dynamically select which Kotlin Multiplatform targets to build.

**Status:** alpha (pre-1.0). The core selector is implemented and exercised by the sample; per-module configuration and helper APIs (`applyHierarchyTemplate`, XCFramework) are roadmap items.

`kmp-targets` is a Gradle plugin for Kotlin Multiplatform projects that lets each developer (and each CI runner) choose which KMP targets to build, via a single Gradle property:

```properties
# local.properties, gradle.properties, or -P
KMP_TARGETS=jvm,iosArm64
```

Targets you don't select are never registered with KGP, so their compile/link/KSP/publish tasks never run — cutting Gradle sync and build times when you only need a subset.

## Usage

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.3.21"
    id("com.rsicarelli.kmptargets") version "<version>"
}
```

Set the selection via any of these sources (priority order, highest first):

1. `-PKMP_TARGETS=...` on the CLI
2. `ORG_GRADLE_PROJECT_KMP_TARGETS` environment variable
3. `gradle.properties` (project, then root)
4. `local.properties` (per-developer, gitignored)
5. Plugin default — every target the plugin currently knows about

### Selection grammar

```properties
KMP_TARGETS=android,iosArm64        # explicit list
KMP_TARGETS=appleMobile             # preset (iosArm64 + iosSimulatorArm64)
KMP_TARGETS=appleMobile,-iosArm64   # preset minus a leaf
KMP_TARGETS=apple,+android          # preset plus an addition
KMP_TARGETS=ANDROID, ios-arm64      # aliases + case-insensitive
```

Available presets: `all`, `apple`, `appleMobile`, `appleDesktop`, `web`, `jvmFamily`.

Unknown tokens fail the build at configuration time with a "did you mean ...?" suggestion — silently dropping a misspelled target in CI is the worst failure mode, so the parser is strict.

### Supported targets

This release ships leaves for the most-used target families. Other branches (`watchOS`, `tvOS`, `linux`, `mingw`, `androidNative`) exist as sealed scaffolding and land their leaves in follow-up releases.

| Family | Targets |
|---|---|
| JVM | `androidTarget` (alias `android`), `jvm` (alias `desktop`) |
| iOS | `iosArm64`, `iosSimulatorArm64` |
| macOS | `macosArm64`, `macosX64` |
| Web | `js`, `wasmJs`, `wasmWasi` |

## Installation

The plugin is not yet published to the Gradle Plugin Portal or Maven Central. Track progress in the issues / releases. Locally:

```bash
task publish-local           # publishes :gradle-plugin to mavenLocal
task sample                  # smoke test against samples/hello-world
```

## Development

Requirements: [mise](https://mise.jdx.dev) (pins JDK 23) and [Task](https://taskfile.dev) (developer task runner).

```bash
mise install        # provisions Temurin 23.0.2+7
task ci             # build + test + sample
task dod            # Definition of Done — fmt + build + test (run before committing)
task hooks:install  # enable the version-controlled .githooks/ pre-commit gate
```

See [CONTRIBUTING.md](./CONTRIBUTING.md) for the full development guide.

## License

Apache-2.0. See [LICENSE](./LICENSE).
