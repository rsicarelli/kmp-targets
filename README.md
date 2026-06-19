# kmp-targets

[![Build](https://img.shields.io/github/actions/workflow/status/rsicarelli/kmp-targets/ci.yml)](https://github.com/rsicarelli/kmp-targets/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.rsicarelli/kmp-targets-gradle-plugin)](https://central.sonatype.com/artifact/com.rsicarelli/kmp-targets-gradle-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2%2B-blue)](https://kotlinlang.org)
[![Docs](https://img.shields.io/badge/docs-rsicarelli.github.io%2Fkmp--targets-blue)](https://rsicarelli.github.io/kmp-targets/)

KMP makes you declare every target up front, then builds them all on every sync, even the ones you're not touching. kmp-targets hands that choice back to you. Pick what to build, and the targets you skip never register, so their tasks never exist.

```kotlin
// declare what this module can build
kmpTargets {
    supports { mobile } // androidTarget + all iOS
}
```
```bash
# Build only what you select.
./gradlew build -Pkmptargets.targets=iosArm64
```
```properties
# ...or set it once as a local preference (kmp-targets.local.properties)
kmptargets.targets=iosArm64
```

## 🤔 Why kmp-targets?

Kotlin Multiplatform makes you declare every target up front, then builds them all on every sync. With two or three targets that's fine. But codebases grow, and every target you add brings its own tasks to compile, link, test, and publish, and more for the IDE to sync. Kotlin's own docs tell you to avoid it: *"[build only for necessary targets](https://kotlinlang.org/docs/native-improving-compilation-time.html#build-only-for-necessary-targets)."* The only built-in way is hand-editing `kotlin { }`.

In practice, you don't work on Android, iOS, web, and desktop at the same time. You pick a platform, iterate, then switch and verify somewhere else. On the iOS simulator today? You don't need the physical-device tasks in your graph. Heading to a real device next? Switch back and they're there. Most of the day a subset is all you need, yet plain KMP builds the whole matrix anyway.

`kmp-targets` splits what a module *supports* from what you *build right now*. Skip a target and it never registers, so none of its tasks get created, and the saving cascades: fewer targets, fewer tasks for Gradle to configure and watch, fewer downloads, a lighter IDE sync. CI works the same way. You still prove every target compiles, but one job rarely needs both device and simulator, so you pick one per check and CI gets shorter too.

It comes out of three years on a 300+ module KMP codebase, where keeping the build set small is what kept iteration fast.

## ⚖️ Before / After

```kotlin
// Plain KMP: every target you list builds on every sync.
kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()
}
```
```kotlin
// kmp-targets: declare what the module supports...
kmpTargets {
    supports { appleMobile + jvm - iosX64 }   // drop the Intel-Mac simulator
}
```
```bash
# ...then select what to build now. The rest never register.
./gradlew build -Pkmptargets.targets=iosArm64
```

## 📊 Impact

`kmp-targets` registers only `selection ∩ supported` targets. Unselected targets are never handed to the Kotlin Gradle Plugin, so **none** of their tasks (compile, klib, metadata, link, test, framework, resources) are ever created. Fewer targets, fewer tasks.

Measured on `samples/hello-world` by counting the `:shared-core:build` task graph via `gradlew --dry-run`:

| Selection | Gradle tasks | vs. all 4 |
| :--- | :---: | :---: |
| `jvm,iosArm64,iosSimulatorArm64,iosX64` | 63 | baseline |
| `jvm,iosArm64,iosSimulatorArm64` | 54 | -14% |
| `jvm,iosArm64` | 38 | -40% |
| `jvm` | 26 | -59% |
| `iosArm64` | 19 | **-70%** |

## ✨ Key Features

- **Targets are set algebra.** Compose presets and leaves with `+`/`-`: `supports { apple + jvm - iosX64 }`.
- **Unselected means non-existent.** Skipped targets never reach KGP, so their tasks never exist.
- **Minimal hierarchy, automatically.** Only the source sets you need, with [no IDE-sync drag](https://rsicarelli.com/en/blog/the-hidden-cost-of-default-hierarchy-templates-in-kotlin-multiplatform/).
- **Opt-in and non-invasive.** Other modules build exactly as before.
- **Layered selection.** A flag, env var, or config file picks the targets.
- **Apple frameworks and XCFrameworks.** `appleFramework("Shared")` attaches to every Apple target.
- **Build-type selection.** `kmptargets.framework.buildTypes=debug` skips the slow Release link.
- **Built-in diagnostics.** `kmpTargetsInfo` and `kmpTargetsDoctor` explain what registered and why.

## ⚡ Quick Start

Requires Gradle 8.11+, JDK 17, and the Kotlin Gradle Plugin 2.2+. You apply KGP yourself; the plugin never applies it for you.

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
    supports { mobile }   // presets (mobile, apple, web, ...) and leaves (jvm, iosArm64, ...) compose with + and -
}
```

**3. Select what to build now.** Use a flag for one run, or a config file to make it durable:

```bash
./gradlew build -Pkmptargets.targets=iosArm64   # the rest never register
```
```properties
# kmp-targets.properties (committed) or kmp-targets.local.properties (personal, git-ignored)
kmptargets.targets=jvm,iosArm64
```

Inspect what the plugin decided at any time with `./gradlew :module:kmpTargetsInfo` (see [🔎 Diagnostics](#-diagnostics)).

> **Ordering:** `supports` registers eagerly, so apply every plugin that influences registration *before* the `kmpTargets { }` block. That matters most for the Android Gradle plugin when you support `androidTarget`.

## 🔎 Diagnostics

Two read-only tasks (group `help`) on every module the plugin is applied to.

`kmpTargetsInfo` shows what resolved, what registered, and why:

```bash
./gradlew :feature-mobile:kmpTargetsInfo -q
```
```console
kmp-targets - :feature-mobile

Selection (what to build now)
  targets:  iosArm64
  source:   command line (-Pkmptargets.targets)

Supported (what this module can build)
  declared: yes
  targets:  androidTarget, iosArm64, iosSimulatorArm64, iosX64

Registered (selection ∩ supported)
  targets:  iosArm64
```

`kmpTargetsDoctor` runs triage (cause, effect, fix) for empty selections, inert modules, host-incompatible targets, disjoint framework build types, and more, or prints a clean bill of health:

```console
kmp-targets doctor - :jvm-tools

✓ clean bill of health - registered: jvm
```

## 📚 Documentation

Everything lives at **[rsicarelli.github.io/kmp-targets](https://rsicarelli.github.io/kmp-targets/)**:

- [Quick Start](https://rsicarelli.github.io/kmp-targets/get-started/quick-start/): apply, declare, select, inspect
- [Selection DSL](https://rsicarelli.github.io/kmp-targets/user-guide/selection-dsl/): `supports { }`, set algebra, the JVM rename
- [Selection Layers](https://rsicarelli.github.io/kmp-targets/user-guide/selection-layers/): config files, precedence, grammar
- [Targets Reference](https://rsicarelli.github.io/kmp-targets/user-guide/targets-reference/): every preset and leaf
- [Apple Framework](https://rsicarelli.github.io/kmp-targets/user-guide/apple-framework/): `appleFramework`, XCFrameworks, `onAppleTarget`
- [Diagnostics](https://rsicarelli.github.io/kmp-targets/user-guide/diagnostics/): `kmpTargetsInfo` and `kmpTargetsDoctor`
- [Advisories & Strict Mode](https://rsicarelli.github.io/kmp-targets/user-guide/advisories/): the eight advisories and CI strictness

## 🤝 Contributing

- **Report a bug or request a feature:** [open an issue](https://github.com/rsicarelli/kmp-targets/issues/new/choose)
- **Develop:** `mise install`, then `task ci` (build + test + sample). Run `task dod` before committing.
- **Guide:** see [CONTRIBUTING.md](./CONTRIBUTING.md)

## License

Apache-2.0 © 2026 Rodrigo Sicarelli. See [LICENSE](./LICENSE).
