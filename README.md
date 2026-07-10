# kmp-targets

[![Build](https://img.shields.io/github/actions/workflow/status/rsicarelli/kmp-targets/ci.yml)](https://github.com/rsicarelli/kmp-targets/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.rsicarelli/kmp-targets-gradle-plugin)](https://central.sonatype.com/artifact/com.rsicarelli/kmp-targets-gradle-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2%2B-blue)](https://kotlinlang.org)
[![Docs](https://img.shields.io/badge/docs-rsicarelli.github.io%2Fkmp--targets-blue)](https://rsicarelli.github.io/kmp-targets/)

Kotlin Multiplatform makes you declare every target a module builds. After that, every Gradle invocation pays for all of them, including IDE sync. Sync doesn't build your targets. It registers them, creates their tasks, and the IDE resolves source sets for each one. That's where the time goes, and it scales with target count whether you're touching those targets or not.

`kmp-targets` separates what a module supports from what you build now. Targets you don't select never register, so their tasks never exist.

```bash
./gradlew build -Pkmptargets.targets=iosArm64
```

On the sample's `shared-core` module, that flag cuts the `build` task graph from 63 tasks to 19, measured with `--dry-run`. Numbers and method in [What was measured](#what-was-measured).

## Why not do it by hand

You can get part of this with `if (project.hasProperty("buildIos"))` in a convention plugin. That approach breaks down at scale:

- **Every module re-implements the check.** Parsing, defaults, and edge cases drift between convention plugins.
- **Typos fail silently.** `-PbuildIso=true` matches nothing, the guard is false, and the target disappears without a trace.
- **No supported vs selected split.** "This module can't build wasm at all" and "skip wasm on this run" are different statements. A boolean flag can't express both, so per-module special cases pile up.
- **No way to inspect the result.** When a target is missing, you read convention-plugin source to find out which condition dropped it.

What the plugin does instead:

- An unknown target name fails the build with a did-you-mean suggestion.
- Presets and set algebra replace hand-rolled string parsing: `apple,jvm,-iosX64`.
- Each module declares what it supports. The registered set is that intersected with the global selection. A selection that matches nothing raises a warning, or fails the build in strict mode.
- Selection resolves through layers with fixed precedence: CLI flag, environment, personal file, committed file.
- Two read-only tasks, `kmpTargetsInfo` and `kmpTargetsDoctor`, print what registered and why.

## Quick Start

See the [compatibility matrix](https://rsicarelli.github.io/kmp-targets/latest/compatibility/) for supported Gradle, JDK, and Kotlin versions.

**1. Add the plugin:**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```
```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0"
}
```

**2. Declare what the module can build:**

```kotlin
kmpTargets {
    supports { jvm + iosArm64 + iosSimulatorArm64 }
}
```

**3. Select what to build now.** A flag for one run, or a config file to make it stick:

```bash
./gradlew build -Pkmptargets.targets=iosArm64
```
```properties
# kmp-targets.local.properties (personal, git-ignored)
# or kmp-targets.properties (committed team default)
kmptargets.targets=jvm,iosArm64
```

With no selection set anywhere, every supported target registers, same as stock KMP.

**4. Inspect what the plugin decided:**

```bash
./gradlew :your-module:kmpTargetsInfo
```

The plugin is opt-in per module. Modules that don't apply it build exactly as before.

## What was measured

Task count of `:shared-core:build` in [`samples/hello-world`](./samples/hello-world), collected with `./gradlew --dry-run`, shrinking the selection from four targets (`jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64`) down to one:

| Selected targets | Tasks in graph | vs baseline |
| :--- | :---: | :---: |
| 4 | 63 | baseline |
| 3 | 54 | -14% |
| 2 | 38 | -40% |
| 1 (`jvm`) | 26 | -59% |
| 1 (`iosArm64`) | 19 | -70% |

The counts drop because unselected targets are never handed to the Kotlin Gradle Plugin, so no compile, klib, metadata, link, test, or framework tasks get created for them.

To be precise about what this number means: 70% fewer tasks in the graph is not 70% faster builds. Fewer registered targets means fewer tasks to create at configuration time, fewer source sets for the IDE to resolve on sync, and fewer compilations when the tasks run. How much wall-clock time that saves depends on your project. Run the same `--dry-run` comparison on your own modules and draw your own conclusions.

The plugin also applies a minimal source-set hierarchy for the registered targets instead of KGP's full default template, which cuts the source-set count the IDE has to resolve ([background](https://rsicarelli.com/en/blog/the-hidden-cost-of-default-hierarchy-templates-in-kotlin-multiplatform/)). Opt out with `kmptargets.hierarchyTemplate=false`.

## Presets and set algebra

Explicit leaf names like `iosArm64` and `jvm` work everywhere. For modules that support a dozen targets, the plugin ships preset names as built-in shorthands. They are fixed, not user-defined:

- `mobile`: `androidTarget` plus the three iOS leaves
- `apple`: every Apple leaf (iOS, macOS, watchOS, tvOS)
- `web`: `js`, `wasmJs`, `wasmWasi`
- `jvmFamily`: `androidTarget`, `jvm`
- `all`: all 25 leaves

More exist (`native`, `appleMobile`, `appleDesktop`, `appleWatch`, `appleTv`, `linux`, `windows`, `androidNative`). The [Targets Reference](https://rsicarelli.github.io/kmp-targets/latest/user-guide/targets-reference/) lists every preset and leaf.

In the DSL, presets and leaves compose with `+` and `-`:

```kotlin
kmpTargets {
    supports { apple + jvm - iosX64 }
}
```

In the selection string, tokens are comma-separated and a `-` prefix drops:

```properties
kmptargets.targets=apple,jvm,-iosX64
```

An unknown name fails the build and suggests the closest match. Family names get a specific hint, for example that `ios` is a family and you want a leaf like `iosArm64` or the `appleMobile` preset.

## Selection layers

The selection resolves through layers. The first one that sets a value wins:

1. CLI flag: `-Pkmptargets.targets=...`
2. Environment: `ORG_GRADLE_PROJECT_kmptargets.targets` (Gradle's standard project-property mapping, useful on CI)
3. `kmp-targets.local.properties`: personal, git-ignored
4. `kmp-targets.properties`: committed, the team default
5. `gradle.properties`
6. `local.properties`

There is also an opt-in Xcode layer (`kmptargets.xcodeEnv=true`) that derives the selection from Xcode's `SDK_NAME` and `ARCHS` during Xcode builds. It sits between 2 and 3. Grammar and precedence details: [Selection Layers](https://rsicarelli.github.io/kmp-targets/latest/user-guide/selection-layers/).

## Apple frameworks

```kotlin
kmpTargets {
    supports { appleMobile + jvm }
    appleFramework("Shared", xcframework = true)
}
```

This attaches a framework named `Shared` to every registered Apple target and registers `assembleSharedXCFramework`, plus per-build-type variants like `assembleSharedDebugXCFramework`.

Build types resolve through the same layers as targets:

```bash
./gradlew assembleSharedXCFramework \
    -Pkmptargets.targets=iosArm64 \
    -Pkmptargets.framework.buildTypes=debug
```

That links the Debug framework only. The Release link tasks never enter the graph. The property narrows the build types declared in the DSL and never adds ones you didn't declare. Full API, including `onAppleTarget` and framework configuration: [Apple Framework](https://rsicarelli.github.io/kmp-targets/latest/user-guide/apple-framework/).

## Diagnostics

Dropping the plugin into an existing module isn't always a one-liner. KSP processors, custom source-set hierarchies, and `expect`/`actual` spread across targets all react to a smaller target set. So the plugin ships tooling that shows what it decided instead of leaving you to guess.

Every module the plugin applies to gets two read-only tasks (group `help`):

- **`kmpTargetsInfo`** prints the resolved selection and which layer it came from, the declared supported set, what registered, and the framework configuration.
- **`kmpTargetsDoctor`** checks the common failure modes and prints cause, effect, and fix for each: a selection that matches nothing the module supports, `androidTarget` selected without the Android Gradle Plugin, targets that can't compile on the current host, modules that registered zero targets, a framework that attached to nothing, disjoint framework build types, and more.

The [Diagnostics docs](https://rsicarelli.github.io/kmp-targets/latest/user-guide/diagnostics/) walk through both, with recipes for the trickier setups. If you get stuck, [open an issue](https://github.com/rsicarelli/kmp-targets/issues/new/choose) and we'll help you wire it up.

## Documentation

Everything lives at [rsicarelli.github.io/kmp-targets](https://rsicarelli.github.io/kmp-targets/):

- [Quick Start](https://rsicarelli.github.io/kmp-targets/latest/get-started/quick-start/): apply, declare, select, inspect
- [Selection DSL](https://rsicarelli.github.io/kmp-targets/latest/user-guide/selection-dsl/): `supports { }`, set algebra, the JVM rename
- [Selection Layers](https://rsicarelli.github.io/kmp-targets/latest/user-guide/selection-layers/): config files, precedence, grammar
- [IDE Workflow](https://rsicarelli.github.io/kmp-targets/latest/user-guide/ide-workflow/): fast Sync on one platform, switch without killing Gradle
- [Targets Reference](https://rsicarelli.github.io/kmp-targets/latest/user-guide/targets-reference/): every preset and leaf
- [Apple Framework](https://rsicarelli.github.io/kmp-targets/latest/user-guide/apple-framework/): `appleFramework`, XCFrameworks, `onAppleTarget`
- [Diagnostics](https://rsicarelli.github.io/kmp-targets/latest/user-guide/diagnostics/): `kmpTargetsInfo` and `kmpTargetsDoctor`
- [Advisories & Strict Mode](https://rsicarelli.github.io/kmp-targets/latest/user-guide/advisories/): the eight advisories and CI strictness

## Contributing

- **Report a bug or request a feature:** [open an issue](https://github.com/rsicarelli/kmp-targets/issues/new/choose)
- **Develop:** `mise install`, then `task ci` (build + test + sample). Run `task dod` before committing.
- **Guide:** see [CONTRIBUTING.md](./CONTRIBUTING.md)

## License

Apache-2.0 © 2026 Rodrigo Sicarelli. See [LICENSE](./LICENSE).
