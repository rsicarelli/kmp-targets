# Changelog

All notable changes to this project will be documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

_Nothing yet — 0.2.0 in development._

## [0.1.0] - 2026-06-17

First public release of the `com.rsicarelli.kmptargets` Gradle plugin.

### Added

- **Property-driven target selection** ([#5]): a single `kmptargets.targets` value decides which
  KMP targets the whole build registers. The plugin registers `selection ∩ supported` per module;
  targets you don't select are **never registered** with KGP, so their compile/link/KSP/publish
  tasks never exist (not merely disabled) — the source of the sync- and build-time savings.
  Selection is host-independent by design.
- **Type-safe selection DSL** ([#14]): `kmpTargets { supports { … } }` declares a module's supported
  set from presets and leaves composed as set algebra (`mobile + web - iosX64`) — no string
  literals, no imports. `defaultSelection` provides a module/project fallback when no global
  selection is set. The DSL is the sole public API; teams wanting `apply-by-id, no body` ergonomics
  build conventions in their own `build-logic/`.
- **Full KMP target vocabulary** ([#7]): every target KGP 2.3.21 ships — all JVM, Apple, Linux,
  MinGW, Android Native and Web leaves (25 leaves), plus presets (`all`, `native`, `apple`,
  `appleMobile`, `appleDesktop`, `appleWatch`, `appleTv`, `linux`, `mingw`, `windows`,
  `androidNative`, `web`, `jvmFamily`, `mobile`). Parsing is case-insensitive with kebab-case
  aliases and a "did you mean …?" suggestion on unknown tokens.
- **Published to Maven Central** ([#59]): group `com.rsicarelli`, artifact
  `kmp-targets-gradle-plugin`, plugin id `com.rsicarelli.kmptargets` (no Gradle Plugin Portal), with
  a sources jar and full POM metadata. Consumers add `mavenCentral()` to
  `pluginManagement.repositories`.
- **Minimal source-set hierarchy template** ([#8]): replaces KGP's default hierarchy with only the
  intermediate source sets the registered targets actually need, collapsing single-child groups to
  drop redundant source sets and tasks. Global toggle `kmptargets.hierarchyTemplate`; the opt-in
  no-collapse mode is [#50] below.
- **`kmpTargetsInfo` introspection task** ([#40]): a read-only, per-module report (group `help`) of
  the resolved selection and its winning source layer, the supported and registered sets, the full
  preset/leaf vocabulary, and any Apple-framework facts — with per-leaf annotations for
  host-incompatible and deprecated leaves, AGP-skipped `androidTarget`, and renamed targets.
- **Strict mode** ([#41]): `kmptargets.strict=true` promotes every advisory to a configuration-time
  build failure (`GradleException`) with identical message text — severity changes, policy does not.
  Default off; recommended for CI only.
- **Host-compatibility advisory** ([#32]): warns when the selection includes a native target the
  current host cannot compile. The target still registers (selection is host-independent), but its
  compile/link tasks fail or are skipped on this host; the host → target matrix tracks your Kotlin
  version via KGP.
- **Deprecated-targets advisory** ([#43]): warns when a Kotlin-deprecated native leaf (`macosX64`,
  `watchosX64`, `tvosX64`, deprecated since Kotlin 2.3.20) registers. They stay selectable and in
  every preset; `kmpTargetsInfo` marks them `(deprecated)`.
- **Empty-overlap advisory** ([#10]): signals when a non-empty selection matches none of a module's
  supported set, so the module registers zero targets — typically a typo'd lane or an over-narrow
  selection.
- **Apple framework helper** ([#105]): `appleFramework("Name") { … }` attaches the real KGP
  `Framework` to every registered Apple leaf (routes to KGP, copies no API); `onAppleTarget { … }`
  is the lower-level per-target primitive for cinterops, extra binaries, and linker options.
- **XCFramework assembly** ([#106]): `appleFramework("Name", xcframework = true)` assembles the
  per-leaf framework slices into a single `.xcframework`.
- **Framework-without-an-Apple-target advisory** ([#107]): warns (and surfaces in `kmpTargetsInfo` /
  `kmpTargetsDoctor`) when a module declares an `appleFramework` but the selection registers no Apple
  target in its `on` scope — the framework attaches to nothing and never builds, which otherwise
  surfaces only as a missing-framework error far downstream in Xcode.
- **Per-lane framework build types** ([#108]): `kmptargets.framework.buildTypes` narrows a
  framework's declared `NativeBuildType`s through the selection-sources chain
  (`effective = property ∩ declared`); a disjoint result raises the framework-build-types-disjoint
  advisory.
- **Xcode-environment selection source** ([#109]): opt-in `kmptargets.xcodeEnv=true` maps Xcode's
  own `SDK_NAME` / `ARCHS` / `CONFIGURATION` to the right leaf and build type, so an Xcode build
  phase drives the selection with no `-P`. Off by default and never read otherwise.
- **ABI-validation coverage signal** ([#81]): warns (and fails under strict) when an ABI task
  (`apiCheck` / `apiDump` or the built-in `checkKotlinAbi` / `updateKotlinAbi`) runs under a narrowed
  selection that leaves some targets unvalidated — ABI-group-aware, zero-config, and dependent on no
  specific ABI tool.
- **Doctor mode — `kmpTargetsDoctor`** ([#82], closes [#80]): a per-module *triage* report (group
  `help`) that complements the neutral `kmpTargetsInfo` state dump. It renders one `[!]` block per
  active advisory — cause → effect → fix — for inert ([#71]), JVM-less metadata ([#72]),
  android-without-AGP ([#51]), host-impossible ([#32]), deprecated ([#43]) and empty-overlap
  ([#10]), plus a doctor-only `single-target KSP` finding ([#73]); a clean bill when healthy; and an
  intentional-silence note for a module that never called
  `supports { }`. **Doctor renders, it does not own predicates**: every finding is wired from the
  same decision function the advisory uses, so it can never re-detect or drift. It also flags
  **project-edge closure gaps** ([#80]) — a `project(...)` dependency that registers none of the
  leaves this module needs — via a companion `kmpTargetsDoctorData` emitter whose file dependents
  consume through a lenient artifact view (file-only, never a peer `Project` read, so it is
  configuration-cache- and **Isolated Projects**-safe). Two honest, permanent limits, printed
  inline: no external-dependency coverage, and an approximate android→jvm fallback — best-effort
  project-edge diagnostics, not a correctness guarantee. See
  [Diagnostics](https://rsicarelli.github.io/kmp-targets/user-guide/diagnostics/).
- **Umbrella lifecycle tasks `kmpCompileAll` / `kmpTestAll`** ([#77]): one stable task name per
  module that depends on the compile (resp. test) tasks of **exactly the registered intersection** —
  selection-agnostic and rename-proof, the cure for hardcoded CI task lists that 404 under a narrowed
  lane or silently match zero tasks under `targetName(jvm, "desktop")` (a literal `compileKotlinJvm`
  compiling nothing while CI stays green). **Opt-in** via `kmptargets.umbrellaTasks=true` (default
  off), since they add dependency edges most builds only want for CI. Wired off the live
  `KotlinTarget`, so the dependency is the genuine task (`compileKotlinDesktop`, `desktopTest`), never
  a name guess. When enabled they are registered in every module, so an unqualified
  `./gradlew kmpCompileAll` from the root fans out to all of them with no cross-project wiring, and a
  module that registered nothing is a clean no-op. Depends on registered **platform** compilations
  only — never `compileCommonMainKotlinMetadata` — so it cannot re-introduce the inert ([#71]) or
  JVM-less-fragment ([#72]) failures. See
  [README → CI → Lane-agnostic compilation](README.md#lane-agnostic-compilation).
- **Per-target configuration-name helpers** ([#74]): `RegisteredTarget.configurationName(prefix)`
  and `RegisteredTarget.testConfigurationName(prefix)` build the `prefix + gradleNameCapitalized`
  (+ `"Test"`) Gradle configuration name a per-target tool publishes — `it.configurationName("ksp")`
  / `it.testConfigurationName("ksp")` instead of hand-rolled `"ksp${it.gradleNameCapitalized}"` and
  a hardcoded `kspJvmTest` that breaks with *Configuration 'kspJvmTest' not found* under
  `targetName(jvm, "desktop")` (it becomes `kspDesktopTest`). Derived from `gradleName`, so the
  names track the rename; tool-generic by design (`it.configurationName("kapt")` works too — the
  plugin blesses no single processor); blank prefix fails loud. Built on the existing
  `gradleNameCapitalized` primitive, so they stay config-cache trivial (pure functions on the
  immutable carrier).
- **Native-only-metadata advisory** ([#72]): when a module **supports** the JVM family
  (`androidTarget`/`jvm`) yet a registration pass ends with **no** JVM-family target registered
  while other targets did register, the plugin logs a one-line advisory. The module is alive — the
  platform klibs compile — but commonMain collapses to a JVM-less shared fragment, so the
  `*KotlinMetadata*` compilations reject JVM-flavored constructs (`@JvmInline` and friends): the
  paradoxical "klibs build, metadata fails". Disjoint from the inert-module advisory by
  construction (inert = zero registrations; this fires only when something *did* register —
  exactly one of the two per module state). Signal only, never filtering; promoted to a build
  failure under `kmptargets.strict=true` with the identical text (the cause advisories —
  empty-overlap, android-without-AGP — are emitted first and win the strict exception where they
  apply; an android leaf skipped by the AGP guard still counts as supported-but-unregistered, so
  both fire in warn mode). The recommended build-logic gate disables only the metadata
  compilations, keyed off `kmpTargets.registered(jvmFamily).isEmpty()`. Any single JVM-family leaf
  registered defeats the predicate (android-only included; the renamed jvm leaf counts — the
  predicate is leaf-based); fires at most once per module, and a later `supports { }` union that
  registers a JVM-family leaf resolves it permanently. `kmpTargetsInfo` renders the same decision
  as a `jvm-less:` line in its registered section.
- **Inert-module advisory** ([#71]): when a module that declared `supports { }` ends a
  registration pass with **zero** targets registered — a disjoint selection, an explicitly-empty
  selection (`jvm,-jvm`, where the empty-overlap advisory is silent by design), or an android-only
  overlap skipped by the AGP guard — the plugin logs a one-line advisory naming the consequence:
  KGP still materializes the commonMain metadata compilation, which fails on a platform-less
  module, and the recommended build-logic gate is `kmpTargets.registered().isEmpty()`. Signal
  only, never filtering; promoted to a build failure under `kmptargets.strict=true` with the
  identical text (the cause advisories — empty-overlap, android-without-AGP — are emitted first
  and win the strict exception where they apply). Fires at most once per module (a later
  `supports { }` union that registers a leaf un-inerts it permanently); a module that never calls
  `supports { }` is deliberately not flagged. `kmpTargetsInfo` renders the same decision as an
  `inert:` line in its registered section.
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

### Documentation

- **Selection-gated eager AGP application — recipe concretized, advisory turned into a discovery
  vehicle** ([#76]). `androidTarget` registers only when an Android Gradle plugin is applied *before*
  `supports { }`; applying AGP to every module is expensive, so the canonical pattern gates it on the
  selection. **No new API, by mechanism-vs-conventions, not omission**: a `whenSelected { }`
  pre-registration hook would have to fire eagerly at call-site to keep AGP ahead of the eager
  `register()` — making it behaviorally identical to
  `if (KmpTarget.Jvm.Android in kmpTargets.resolvedSelection()) { … }` over the existing
  [`resolvedSelection()`](#52) primitive, with zero added mechanism and a footgun (apply-late) it
  cannot remove by construction, and the `com.android.application` exemption is a build-logic decision
  the plugin cannot make one-module-at-a-time. So the deliverable is docs + signal: the
  [recipe](docs/user-guide/recipes.md#selection-gated-eager-agp-application) now spells out the real
  `resolvedSelection()` gate, the application-module exemption (an app is always Android — never
  double-apply or gate it), the apply-before-`supports { }` ordering rule, the **downstream-read
  gating** (a `namespace`/manifest read crashes under non-Android selections unless guarded by the
  same predicate), and companion-plugin reactions via `pluginManager.withPlugin(...)`; the
  android-without-AGP advisory message now appends a one-line selection-gate hint and links that
  recipe; and [advisories](docs/user-guide/advisories.md#android-target-without-agp) cross-links it.
- **Selection vs the dependency graph — the android→jvm fallback rule** ([#80]). Selection is
  resolved per module, but its *validity* can depend on the cross-module dependency graph: an
  android-leaning module that consumes a `jvm`+native-only library resolves against that library's
  **jvm fallback** variant, so a pure-`android` selection — which strips `jvm` from every producer —
  removes the variant the android modules were resolving against. The failure surfaces as Gradle's
  raw attribute-resolution wall of text on a `compileDependencyFiles` configuration, with no hint
  the selection is the cause; the fix is to keep `jvm` **co-selected** (`kmptargets.targets=android,jvm`).
  **No new API, by feasibility, not omission**: an accurate in-code closure check would have to read
  peer projects' registered sets at configuration time — forbidden by the plugin's
  configuration-cache and Isolated-Projects guarantees — and the canonical trigger is *external*
  published libraries the plugin cannot model one-module-at-a-time. So the deliverable is docs: the
  [recipe](docs/user-guide/recipes.md) gains the asymmetric android→jvm case,
  [troubleshooting](docs/help/troubleshooting.md) gains a `compileDependencyFiles` row, and the
  closure analysis is deferred to a future doctor mode ([#82]). Adjacent hygiene: the
  [advisories](docs/user-guide/advisories.md) page now documents the **native-only-metadata**
  advisory ([#72]).
- **commonMain-KSP recipe — corrected to the real rule, plus a doctor finding** ([#73]). A processor
  that generates into `commonMain` runs on the shared commonMain metadata route
  (`kspCommonMainKotlinMetadata`). The earlier guidance claimed that route "needs a **native**
  target" and gated on `registered(KmpTargetSet.native).isEmpty()` — but that was an unverified
  assumption, and it was **actively harmful** (it disabled compilation on working `jvm`/`android,jvm`
  lanes). A real-KSP measurement (the ktorfit sample, KSP 2.3.9 / Kotlin 2.3.21) shows
  `kspCommonMainKotlinMetadata` follows the **same ≥2-platform-targets rule** as KGP's
  `compileCommonMainKotlinMetadata`: `jvm,js` (zero native) generates into
  `build/generated/ksp/metadata/commonMain/`, while `iosArm64` alone (native) does not. The real
  trap is a **single target**, native or not. The recipe now states the target-count rule, gates on
  `registered().size < 2`, and gives the structural fix our sample proved — keep codegen-consuming
  code in the target source set (e.g. `androidMain`), or register a second target. Because the
  single-target + KSP-applied facts **are** observable (even though "generates into commonMain?" is
  not), this adds a doctor-only `single-target KSP` finding (no build-time advisory, no strict
  escalation). Backed by tests: the gate predicate (`CommonMainKspSingleTargetGateTest`), the
  decision function, the finding prose, the in-process `compileCommonMainKotlinMetadata` invariant,
  and a real-KSP tripwire pinning `kspCommonMainKotlinMetadata` to the ≥2-target rule — a KGP/KSP
  bump that shifts it fails loudly.

### Fixed

- **Plugin jar is now consumable from Gradle 8.x `kotlin-dsl` build-logic and JDK 17 daemons**
  ([#48]). The jar previously carried Kotlin 2.3 metadata (rejected by the Gradle-embedded Kotlin
  2.0.x compiler on Gradle 8.11–8.14) and Java 23 bytecode (unloadable on JDK 17). The build now
  drops `jvmToolchain(23)` and pins the emitted output instead — `languageVersion`/`apiVersion`
  2.0, `jvmTarget` 17 + `-Xjdk-release=17` / `--release 17`, and `coreLibrariesVersion` 2.0.21 so
  the POM stops declaring a toolchain-version stdlib. A new `verifyCompatFloors` task guards the
  floors on every `check`.

[#5]: https://github.com/rsicarelli/kmp-targets/issues/5
[#7]: https://github.com/rsicarelli/kmp-targets/issues/7
[#8]: https://github.com/rsicarelli/kmp-targets/issues/8
[#10]: https://github.com/rsicarelli/kmp-targets/issues/10
[#14]: https://github.com/rsicarelli/kmp-targets/issues/14
[#30]: https://github.com/rsicarelli/kmp-targets/issues/30
[#32]: https://github.com/rsicarelli/kmp-targets/issues/32
[#40]: https://github.com/rsicarelli/kmp-targets/issues/40
[#41]: https://github.com/rsicarelli/kmp-targets/issues/41
[#43]: https://github.com/rsicarelli/kmp-targets/issues/43
[#48]: https://github.com/rsicarelli/kmp-targets/issues/48
[#49]: https://github.com/rsicarelli/kmp-targets/issues/49
[#50]: https://github.com/rsicarelli/kmp-targets/issues/50
[#51]: https://github.com/rsicarelli/kmp-targets/issues/51
[#52]: https://github.com/rsicarelli/kmp-targets/issues/52
[#59]: https://github.com/rsicarelli/kmp-targets/issues/59
[#71]: https://github.com/rsicarelli/kmp-targets/issues/71
[#72]: https://github.com/rsicarelli/kmp-targets/issues/72
[#73]: https://github.com/rsicarelli/kmp-targets/issues/73
[#74]: https://github.com/rsicarelli/kmp-targets/issues/74
[#76]: https://github.com/rsicarelli/kmp-targets/issues/76
[#77]: https://github.com/rsicarelli/kmp-targets/issues/77
[#80]: https://github.com/rsicarelli/kmp-targets/issues/80
[#81]: https://github.com/rsicarelli/kmp-targets/issues/81
[#82]: https://github.com/rsicarelli/kmp-targets/issues/82
[#105]: https://github.com/rsicarelli/kmp-targets/issues/105
[#106]: https://github.com/rsicarelli/kmp-targets/issues/106
[#107]: https://github.com/rsicarelli/kmp-targets/issues/107
[#108]: https://github.com/rsicarelli/kmp-targets/issues/108
[#109]: https://github.com/rsicarelli/kmp-targets/issues/109

[Unreleased]: https://github.com/rsicarelli/kmp-targets/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/rsicarelli/kmp-targets/releases/tag/v0.1.0
