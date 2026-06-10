# Changelog

All notable changes to this project will be documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- **Doctor mode — `kmpTargetsDoctor`** ([#82], closes [#80]): a per-module *triage* report (group
  `help`) that complements the neutral `kmpTargetsInfo` state dump. It renders one `[!]` block per
  active advisory — cause → effect → fix — for inert ([#71]), JVM-less metadata ([#72]),
  android-without-AGP ([#51]), host-impossible ([#32]), deprecated ([#43]) and empty-overlap
  ([#10]); a clean bill when healthy; and an intentional-silence note for a module that never called
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
  advisory ([#72]) and counts six advisories, not five.
- **commonMain-KSP-needs-a-native-target recipe, hardened** ([#73]). A processor that generates
  into `commonMain` runs on the shared commonMain metadata route (`kspCommonMainKotlinMetadata`),
  which KSP only wires when the selection registers a **native** target — so a native-less lane
  silently skips generation (worst case: a green build shipping missing codegen). **No new API**:
  the trap's distinguishing fact ("this module runs a commonMain metadata-route processor") is one
  the plugin cannot observe, so per the mechanism-vs-conventions split it stays in build-logic,
  which gates on the existing `registered(KmpTargetSet.native)` primitive ([#52]). The user-guide
  recipe now warns **and** disables the doomed compilations, notes that K2's strict fragment
  resolution rules out a per-platform workaround, and is backed by two tests: one pinning the gate
  predicate, one pinning the actual KGP 2.3.21 rule for `compileCommonMainKotlinMetadata` — which
  tracks a *shared* commonMain (≥2 platform targets), **not** native presence (`jvm + js` has it
  with zero native targets; a lone `iosArm64` does not). That invariant test is a tripwire: a KGP
  bump that shifts the rule fails it loudly.

### Fixed

- **Plugin jar is now consumable from Gradle 8.x `kotlin-dsl` build-logic and JDK 17 daemons**
  ([#48]). The jar previously carried Kotlin 2.3 metadata (rejected by the Gradle-embedded Kotlin
  2.0.x compiler on Gradle 8.11–8.14) and Java 23 bytecode (unloadable on JDK 17). The build now
  drops `jvmToolchain(23)` and pins the emitted output instead — `languageVersion`/`apiVersion`
  2.0, `jvmTarget` 17 + `-Xjdk-release=17` / `--release 17`, and `coreLibrariesVersion` 2.0.21 so
  the POM stops declaring a toolchain-version stdlib. A new `verifyCompatFloors` task guards the
  floors on every `check`.

[#10]: https://github.com/rsicarelli/kmp-targets/issues/10
[#30]: https://github.com/rsicarelli/kmp-targets/issues/30
[#32]: https://github.com/rsicarelli/kmp-targets/issues/32
[#43]: https://github.com/rsicarelli/kmp-targets/issues/43
[#48]: https://github.com/rsicarelli/kmp-targets/issues/48
[#49]: https://github.com/rsicarelli/kmp-targets/issues/49
[#50]: https://github.com/rsicarelli/kmp-targets/issues/50
[#51]: https://github.com/rsicarelli/kmp-targets/issues/51
[#52]: https://github.com/rsicarelli/kmp-targets/issues/52
[#71]: https://github.com/rsicarelli/kmp-targets/issues/71
[#72]: https://github.com/rsicarelli/kmp-targets/issues/72
[#73]: https://github.com/rsicarelli/kmp-targets/issues/73
[#74]: https://github.com/rsicarelli/kmp-targets/issues/74
[#76]: https://github.com/rsicarelli/kmp-targets/issues/76
[#77]: https://github.com/rsicarelli/kmp-targets/issues/77
[#80]: https://github.com/rsicarelli/kmp-targets/issues/80
[#82]: https://github.com/rsicarelli/kmp-targets/issues/82
