# Changelog

All notable changes to this project will be documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project is pre-1.0, so breaking
changes may land in any release.

## [Unreleased]

### Changed (BREAKING)

- **Unified property naming under the `kmptargets.` namespace** ([#31]). The selection key is
  renamed; the hierarchy key was already on-convention and stays. This is a hard break — the old
  names are **not read at all** (no deprecated alias): a build still setting only `KMP_TARGETS`
  falls through to `defaultSelection` / the plugin default.

  | Concern | Before | After |
  |---|---|---|
  | Selection (`gradle.properties` / files / `local.properties`) | `KMP_TARGETS=...` | `kmptargets.targets=...` |
  | Selection (CLI) | `-PKMP_TARGETS=...` | `-Pkmptargets.targets=...` |
  | Selection (env) | `ORG_GRADLE_PROJECT_KMP_TARGETS` or bare `KMP_TARGETS` | `ORG_GRADLE_PROJECT_kmptargets.targets` |
  | Hierarchy template | `kmptargets.hierarchyTemplate` | `kmptargets.hierarchyTemplate` (unchanged) |

- The bare `KMP_TARGETS` **environment variable** is no longer read; the env surface is Gradle's
  native `ORG_GRADLE_PROJECT_<key>` mapping only (which now also works for
  `kmptargets.hierarchyTemplate`, which previously had no env form).

### Added

- **Per-module JVM target name override** ([#49]): `kmpTargets { targetName(jvm, "desktop") }`
  registers the jvm leaf under a custom Gradle target name, so legacy `jvm("desktop")` codebases
  keep their `src/desktopMain` dirs, `-desktop` artifact suffixes, and `compileKotlinDesktop` task
  names. jvm leaf only (androidTarget is named by AGP; native/web names are derived by KGP); must
  be called **before** `supports { }` (eager one-way registration — fails fast otherwise); the
  selection token stays `jvm`/`desktop`. The hierarchy template needs no change — KGP's `withJvm()`
  matches by platform type, not name. `kmpTargetsInfo` annotates the registered line:
  `jvm (registered as: desktop)`.
- **Dedicated root config files** ([#30]): `kmp-targets.properties` (committed, team-shared) and
  `kmp-targets.local.properties` (git-ignored personal override, Bazel `try-import` semantics) are
  the consolidation point for global configuration. Full precedence chain (highest first): CLI `-P`
  → `ORG_GRADLE_PROJECT_<key>` env → personal file → committed file → root `gradle.properties` →
  `local.properties` → `defaultSelection` → built-in. Both global keys
  (`kmptargets.targets`, `kmptargets.hierarchyTemplate`) read through the same chain.
- **Fail-loud key validation**: an unknown key in either dedicated file fails the build at
  configuration time with a "did you mean …?" suggestion.
- Both dedicated files are tracked configuration-cache inputs: edits invalidate the cache,
  untouched files keep cache hits.
- **Opt-in no-collapse hierarchy mode** ([#50]): `kmptargets.hierarchyCollapse=false` (global key)
  or `kmpTargets { collapseHierarchy.set(false) }` (per-module, wins) makes a group materialize
  with **≥1** present child instead of ≥2, so intermediates like `iosMain` survive a single-leaf
  selection — for codebases with load-bearing `src/iosMain` dirs. Default unchanged (`true`,
  collapse); never changes what registers; no-op when `kmptargets.hierarchyTemplate=false`.
- **Registered-targets query surface for build-logic** ([#52]): `registered()` /
  `registered(slice)` snapshot what the plugin **actually registered** as a `KmpTargetSet`
  (family-sliceable with the existing presets: `registered(KmpTargetSet.appleMobile)`), and
  `onRegistered { … }` delivers a `RegisteredTarget` per leaf with `configureEach` semantics
  (replays for already-registered leaves, fires per delta of later `supports { }` unions) — so
  per-target wiring like KSP's `ksp<Target>` configurations needs no KGP konan imports:
  `onRegistered { add("ksp${it.gradleNameCapitalized}", dep) }`. The carrier's `gradleName` is the
  name registration actually used (a renamed jvm reports `desktop`); `registered()` mirrors real
  registrations, not the abstract plan — empty without KGP, and the #51-skipped `androidTarget`
  stays absent.
- **Android-without-AGP advisory** ([#51]): when a module both selects and supports
  `androidTarget` but no Android Gradle plugin is applied by the time `supports { }` runs, the
  plugin skips registering that leaf — the KGP alternative is the fatal
  `AndroidGradlePluginIsMissing` crash — and logs a one-line advisory naming the module and the
  fix (apply `com.android.library`/`com.android.application` before `supports { }`), promoted to
  a build failure under `kmptargets.strict=true` with the identical text. The detection mirrors
  KGP's full Android plugin id list, the advisory fires at most once per module, and
  `kmpTargetsInfo` marks the leaf `androidTarget (skipped: no Android plugin applied)` while the
  condition holds.

### Fixed

- **Plugin jar is now consumable from Gradle 8.x `kotlin-dsl` build-logic and JDK 17 daemons**
  ([#48]). The jar previously carried Kotlin 2.3 metadata (rejected by the Gradle-embedded Kotlin
  2.0.x compiler on Gradle 8.11–8.14) and Java 23 bytecode (unloadable on JDK 17). The build now
  drops `jvmToolchain(23)` and pins the emitted output instead — `languageVersion`/`apiVersion`
  2.0, `jvmTarget` 17 + `-Xjdk-release=17` / `--release 17`, and `coreLibrariesVersion` 2.0.21 so
  the POM stops declaring a toolchain-version stdlib. A new `verifyCompatFloors` task guards the
  floors on every `check`.

[#30]: https://github.com/rsicarelli/kmp-targets/issues/30
[#31]: https://github.com/rsicarelli/kmp-targets/issues/31
[#48]: https://github.com/rsicarelli/kmp-targets/issues/48
[#49]: https://github.com/rsicarelli/kmp-targets/issues/49
[#50]: https://github.com/rsicarelli/kmp-targets/issues/50
[#51]: https://github.com/rsicarelli/kmp-targets/issues/51
[#52]: https://github.com/rsicarelli/kmp-targets/issues/52
