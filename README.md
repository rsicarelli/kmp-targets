# kmp-targets

[![Build](https://img.shields.io/github/actions/workflow/status/rsicarelli/kmp-targets/ci.yml)](https://github.com/rsicarelli/kmp-targets/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.rsicarelli/kmp-targets-gradle-plugin)](https://central.sonatype.com/artifact/com.rsicarelli/kmp-targets-gradle-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2%2B-blue)](https://kotlinlang.org)
[![Docs](https://img.shields.io/badge/docs-rsicarelli.github.io%2Fkmp--targets-blue)](https://rsicarelli.github.io/kmp-targets/)

**Build only for necessary targets.** Choose which Kotlin Multiplatform targets compile with one Gradle property — the ones you skip never register, so their tasks never exist.

```kotlin
// build.gradle.kts — declare what this module CAN build
kmpTargets { supports { mobile } }   // androidTarget + all iOS
```
```bash
# Build only what you select — unselected targets never register
./gradlew build -Pkmptargets.targets=iosArm64
```

## 🤔 Why kmp-targets?

Kotlin Multiplatform makes you declare every target up front, then builds them all on every sync. Kotlin's own docs tell you not to: *"[build only for necessary targets](https://kotlinlang.org/docs/native-improving-compilation-time.html#build-only-for-necessary-targets)."* The catch is that the only built-in way to do it is hand-editing `kotlin { }`.

`kmp-targets` turns that into one property. Pick the set you want and the targets you skip never register, so their tasks never exist.

## ⚖️ Before / After

```kotlin
// Before — plain KMP: all three targets build on every sync
kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
}
```
```kotlin
// After — kmp-targets: declare the set once…
kmpTargets { supports { jvm + iosArm64 + iosSimulatorArm64 } }
```
```bash
# …then build only what you're working on. jvm + the simulator never register.
./gradlew build -Pkmptargets.targets=iosArm64
```

The `jvm` and simulator tasks are never created — and it scales (see [📊 Impact](#-impact)).

## 📊 Impact

`kmp-targets` registers only `selection ∩ supported` targets. Unselected targets are never handed to the Kotlin Gradle Plugin, so **none** of their tasks (compile, klib, metadata, link, test, framework, resources) are ever created. Fewer targets ⇒ a strictly smaller task graph.

Measured on `samples/hello-world`'s `shared-core` module (`supports { all }`) by counting the `:shared-core:build` task graph via `gradlew --dry-run`:

| Targets registered | Selection | Gradle tasks | vs. all 4 |
| :---: | :--- | :---: | :---: |
| 4 | `jvm,iosArm64,iosSimulatorArm64,iosX64` | 63 | — |
| 3 | `jvm,iosArm64,iosSimulatorArm64` | 54 | −14% |
| 2 | `jvm,iosArm64` | 38 | −40% |
| 1 | `jvm` | 26 | −59% |
| 1 | `iosArm64` | 19 | **−70%** |

A module that declares an `appleFramework` collapses even harder — each dropped Apple leaf also removes its `linkDebugFramework…` / `linkReleaseFramework…` tasks. On `core-and-apple` (`supports { apple + jvm }` + `appleFramework`): **86 tasks** for 5 targets → **21 tasks** for `iosArm64` alone (**−76%**).

<sub>Measured with Gradle 9.5.1 / Kotlin 2.3.21 via `--dry-run`. Counts are the per-module `build` task graph and include the sample's ktfmt tasks; the relative reduction holds with those excluded (shared-core 49 → 13 KGP-only tasks, −73%).</sub>

## ✨ Key Features

- **Targets as set algebra** — Compose `supports { apple + jvm - iosX64 }` from type-safe presets and leaves — no magic strings.
- **Unselected means non-existent** — Skipped targets never reach KGP, so their compile, link, test, and KSP tasks are never created — not merely skipped.
- **Opt-in and non-invasive** — The plugin only touches modules that apply it; plain KMP modules build exactly as before. Mix them freely.
- **Layered selection** — Choose targets via `-P` flag, env var, per-developer file, committed team file, or `gradle.properties` — with "did you mean?" on typos.
- **Apple frameworks & XCFrameworks** — Declare `appleFramework("Shared")` once; it attaches to every registered Apple target, XCFramework optional.
- **Built-in diagnostics** — `kmpTargetsInfo` and `kmpTargetsDoctor` show what's selected, supported, and registered — and why a target is inert.

## ⚡ Quick Start

Requires Gradle 8.11+, JDK 17, and the Kotlin Gradle Plugin 2.2+ (you apply KGP yourself — the plugin never applies it for you).

**1. Add the plugin.** It's published to Maven Central (not the Gradle Plugin Portal), so make sure `mavenCentral()` is in your plugin repositories:

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
    supports { mobile }   // presets (mobile, apple, web, …) and leaves (jvm, iosArm64, …) compose with + and -
}
```

**3. Select what to build now** — a flag for one run, or a config file to make it durable:

```bash
./gradlew build -Pkmptargets.targets=iosArm64   # the rest never register
```
```properties
# kmp-targets.properties (committed) or kmp-targets.local.properties (personal, git-ignored)
kmptargets.targets=jvm,iosArm64
```

Inspect what the plugin decided at any time with `./gradlew :module:kmpTargetsInfo` — see [🔎 Diagnostics](#-diagnostics).

> **Ordering:** `supports` registers eagerly. Apply every plugin that influences registration — especially the Android Gradle plugin when you support `androidTarget` — *before* the `kmpTargets { }` block.

## 🧩 Automatic hierarchy

`kmp-targets` rebuilds the source-set hierarchy to fit exactly the targets you registered — no manual `sourceSets { }` wiring, no redundant intermediates. It keeps only the groups that actually merge two or more children.

```text
KGP default — 3 redundant intermediates for 2 iOS leaves
commonMain
└── nativeMain
    └── appleMain
        └── iosMain
            ├── iosArm64Main
            └── iosSimulatorArm64Main

kmp-targets — only the intermediate that matters
commonMain
└── iosMain
    ├── iosArm64Main
    └── iosSimulatorArm64Main
```

A single iOS leaf collapses all the way to `commonMain` (zero intermediates). Tune per module via the `hierarchyTemplate` and `collapseHierarchy` flags.

## 🛠️ Build-type selection

Kotlin's docs also warn that *"[compilation of release binaries takes an order of magnitude more time](https://kotlinlang.org/docs/native-improving-compilation-time.html#don-t-build-unnecessary-release-binaries)"* than debug — so don't link them while you iterate. `kmp-targets` makes the framework build type a lane knob, exactly like target selection:

```properties
# kmp-targets.properties — team default: fast local links
kmptargets.framework.buildTypes=debug
```
```bash
# a release lane overrides it, exactly like the targets key
./gradlew assembleKotlinSharedXCFramework -Pkmptargets.framework.buildTypes=release
```

`=debug` leaves only the `linkDebugFramework…` and `assemble…DebugXCFramework` tasks; the slow Release link never runs. The effective set is `property ∩ appleFramework(buildTypes = …)` — a lane only ever *narrows* what the module declared.

## 🔎 Diagnostics

Two read-only tasks (group `help`) on every module the plugin is applied to.

`kmpTargetsInfo` — what resolved, what registered, and why:

```bash
./gradlew :feature-mobile:kmpTargetsInfo -q
```
```console
kmp-targets — :feature-mobile

Selection (what to build now)
  targets:  iosArm64
  source:   command line (-Pkmptargets.targets)

Supported (what this module can build)
  declared: yes
  targets:  androidTarget, iosArm64, iosSimulatorArm64, iosX64

Registered (selection ∩ supported)
  targets:  iosArm64
```

`kmpTargetsDoctor` — triage (cause → effect → fix) for empty selections, inert modules, host-incompatible targets, disjoint framework build types, and more — or a clean bill of health:

```console
kmp-targets doctor — :jvm-tools

✓ clean bill of health — registered: jvm
```

## 📚 Documentation

Everything lives at **[rsicarelli.github.io/kmp-targets](https://rsicarelli.github.io/kmp-targets/)**:

- [Quick Start](https://rsicarelli.github.io/kmp-targets/get-started/quick-start/) — apply, declare, select, inspect
- [Selection DSL](https://rsicarelli.github.io/kmp-targets/user-guide/selection-dsl/) — `supports { }`, set algebra, the JVM rename
- [Selection Layers](https://rsicarelli.github.io/kmp-targets/user-guide/selection-layers/) — config files, precedence, grammar
- [Targets Reference](https://rsicarelli.github.io/kmp-targets/user-guide/targets-reference/) — every preset and leaf
- [Apple Framework](https://rsicarelli.github.io/kmp-targets/user-guide/apple-framework/) — `appleFramework`, XCFrameworks, `onAppleTarget`
- [Diagnostics](https://rsicarelli.github.io/kmp-targets/user-guide/diagnostics/) — `kmpTargetsInfo` and `kmpTargetsDoctor`
- [Advisories & Strict Mode](https://rsicarelli.github.io/kmp-targets/user-guide/advisories/) — the eight advisories and CI strictness

## 🤝 Contributing

- **Report a bug or request a feature** — [open an issue](https://github.com/rsicarelli/kmp-targets/issues/new/choose)
- **Develop** — `mise install`, then `task ci` (build + test + sample); run `task dod` before committing
- **Guide** — see [CONTRIBUTING.md](./CONTRIBUTING.md)

## License

Apache-2.0 © 2026 Rodrigo Sicarelli. See [LICENSE](./LICENSE).
