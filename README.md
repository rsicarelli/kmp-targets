# kmp-targets

> Dynamically select which Kotlin Multiplatform targets to build.

[![Build](https://img.shields.io/github/actions/workflow/status/rsicarelli/kmp-targets/ci.yml)](https://github.com/rsicarelli/kmp-targets/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.rsicarelli/kmp-targets-gradle-plugin)](https://central.sonatype.com/artifact/com.rsicarelli/kmp-targets-gradle-plugin)
[![Docs](https://img.shields.io/badge/docs-rsicarelli.github.io%2Fkmp--targets-blue)](https://rsicarelli.github.io/kmp-targets/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

`kmp-targets` is a Gradle plugin that lets each developer (and each CI runner) choose which KMP targets to build, via a single Gradle property:

```properties
# kmp-targets.properties (committed), kmp-targets.local.properties (personal), or -P
kmptargets.targets=jvm,iosArm64
```

Targets you don't select are never registered with KGP, so their compile/link/KSP/publish tasks never exist — cutting sync and build times when you only need a subset.

## Example

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0"
}

kmpTargets {
    supports { mobile + web - iosX64 }   // presets and leaves compose as set algebra
}
```

`supports { }` declares what the module *can* build; the global `kmptargets.targets` selection decides what you *want* built now. The plugin registers `selection ∩ supported` per module. Inspect any module's resolved state with `./gradlew :module:kmpTargetsInfo`.

## Installation

Published to Maven Central — group `com.rsicarelli`, artifact `kmp-targets-gradle-plugin`, plugin id `com.rsicarelli.kmptargets` (no Gradle Plugin Portal). With `mavenCentral()` in `pluginManagement.repositories`:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0"
}
```

Version catalogs, snapshots, and build-logic dependencies: [Get Started](https://rsicarelli.github.io/kmp-targets/get-started/).

## Requirements

| Consumer surface | Floor |
|---|---|
| Gradle (`kotlin-dsl` build-logic) | **8.11+** |
| Daemon JVM | **JDK 17** |
| Kotlin Gradle Plugin | **2.2+** |

Full story: [Compatibility](https://rsicarelli.github.io/kmp-targets/compatibility/).

## Documentation

**Status:** alpha. Roadmap: user-defined hierarchy groups. The [Apple framework](https://rsicarelli.github.io/kmp-targets/user-guide/apple-framework/) helper covers framework declaration and XCFramework assembly, and the opt-in [Xcode environment](https://rsicarelli.github.io/kmp-targets/user-guide/xcode-environment/) source lets Xcode's `SDK_NAME`/`ARCHS`/`CONFIGURATION` drive selection with no `-P`.

Everything lives at **[rsicarelli.github.io/kmp-targets](https://rsicarelli.github.io/kmp-targets/)**:

- [Quick Start](https://rsicarelli.github.io/kmp-targets/get-started/quick-start/) — apply, declare, select, inspect
- [Selection DSL](https://rsicarelli.github.io/kmp-targets/user-guide/selection-dsl/) — `supports { }`, set algebra, the JVM rename
- [Selection Layers](https://rsicarelli.github.io/kmp-targets/user-guide/selection-layers/) — config files, precedence, grammar
- [Targets Reference](https://rsicarelli.github.io/kmp-targets/user-guide/targets-reference/) — every preset and leaf
- [Hierarchy](https://rsicarelli.github.io/kmp-targets/user-guide/hierarchy-template/) — minimal template, no-collapse
- [Build Logic](https://rsicarelli.github.io/kmp-targets/user-guide/build-logic/) — conventions, `onRegistered`, helpers
- [Apple Framework](https://rsicarelli.github.io/kmp-targets/user-guide/apple-framework/) — `appleFramework`, `onAppleTarget`, route-to-real-KGP
- [Xcode Environment](https://rsicarelli.github.io/kmp-targets/user-guide/xcode-environment/) — opt-in `SDK_NAME`/`ARCHS`/`CONFIGURATION` selection, zero-`-P` Xcode builds
- [Recipes](https://rsicarelli.github.io/kmp-targets/user-guide/recipes/) — KSP gates, AGP gating, dependency-graph rules
- [Advisories & Strict Mode](https://rsicarelli.github.io/kmp-targets/user-guide/advisories/) — the eight advisories and CI strictness
- [Diagnostics](https://rsicarelli.github.io/kmp-targets/user-guide/diagnostics/) — `kmpTargetsInfo` and `kmpTargetsDoctor`
- [CI](https://rsicarelli.github.io/kmp-targets/user-guide/ci-matrix/) — per-host matrix, umbrella tasks
- [Design](https://rsicarelli.github.io/kmp-targets/why-kmp-targets/) — rationale and trade-offs
- [Troubleshooting](https://rsicarelli.github.io/kmp-targets/help/troubleshooting/) — symptom → cause → fix

## Development

Requirements: [mise](https://mise.jdx.dev) (pins JDK 23) and [Task](https://taskfile.dev).

```bash
mise install        # provisions Temurin 23.0.2+7
task ci             # build + test + sample
task dod            # Definition of Done — fmt + build + test (run before committing)
task hooks:install  # enable the version-controlled .githooks/ pre-commit gate
```

See [CONTRIBUTING.md](./CONTRIBUTING.md) for the full development guide.

## License

Apache-2.0. See [LICENSE](./LICENSE).
