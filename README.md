# kmp-targets

> Dynamically select which Kotlin Multiplatform targets to build.

[![Build](https://img.shields.io/github/actions/workflow/status/rsicarelli/kmp-targets/ci.yml)](https://github.com/rsicarelli/kmp-targets/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.rsicarelli/kmp-targets-gradle-plugin)](https://central.sonatype.com/artifact/com.rsicarelli/kmp-targets-gradle-plugin)
[![Docs](https://img.shields.io/badge/docs-rsicarelli.github.io%2Fkmp--targets-blue)](https://rsicarelli.github.io/kmp-targets/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Status:** alpha (pre-1.0). Shipped and exercised by the multi-module sample plus a real 3-OS CI matrix: the global selector with [try-import-style layering](#selecting-targets) (dedicated config files, unified `kmptargets.*` keys), the automatic [minimal hierarchy template](#minimal-hierarchy-template), the [`kmpTargetsInfo`](#debugging-the-selection) introspection task, [host-compatibility](#host-compatibility), [deprecated-target](#deprecated-targets), [android-without-AGP](#android-target-without-the-android-gradle-plugin), [inert-module](#inert-modules), and [native-only-metadata](#native-only-metadata-jvm-less-commonmain) advisories, and opt-in [strict mode](#strict-mode). Roadmap: user-defined hierarchy groups, XCFramework helpers.

`kmp-targets` is a Gradle plugin for Kotlin Multiplatform projects that lets each developer (and each CI runner) choose which KMP targets to build, via a single Gradle property:

```properties
# kmp-targets.properties (committed), kmp-targets.local.properties (personal), gradle.properties, or -P
kmptargets.targets=jvm,iosArm64
```

Targets you don't select are never registered with KGP, so their compile/link/KSP/publish tasks never run — cutting Gradle sync and build times when you only need a subset.

## Usage

A real multi-module project is heterogeneous: a shared module builds every platform, a mobile
feature builds only Android + iOS, a tooling module builds only the JVM. `kmp-targets` separates two
facts:

- **What a module *can* build** — its *supported* set, declared per-module via the type-safe DSL.
- **What you *want* to build now** — the global `kmptargets.targets` *selection*.

The plugin registers `selection ∩ supported` for each module. A module never builds a target outside
its supported set, and the global selection narrows that further.

### Applying the plugin

Apply KGP and the `com.rsicarelli.kmptargets` base id together, then declare the module's supported
set with the type-safe `kmpTargets { supports { … } }` DSL — the whole target vocabulary is in scope
as set algebra (`+` / `-`), no imports, no strings:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.3.21"
    id("com.rsicarelli.kmptargets") version "<version>"
}

kmpTargets {
    supports { mobile + web - iosX64 }      // presets and leaves compose freely
}
```

Every preset (`all`, `mobile`, `apple`, `web`, `jvmFamily`, …) and every leaf (`jvm`, `iosArm64`,
`linuxX64`, …) is available inside the block. Build-logic and consumers needing a raw value can use
the overload: `supports(KmpTargetSet.mobile + KmpTargetSet.web)`.

Targets are **explicit**: a module that never calls `supports` registers nothing, just like plain
KGP where every target is declared by hand. `supports` registers eagerly — the moment it runs — so
call it before anything reads `kotlin.targets`, and calling it more than once **unions** (an
already-registered target can't be retracted). To build whatever the developer is currently
targeting, opt in explicitly with `supports { all }` — then the global `kmptargets.targets` alone
decides what registers.

The selection itself is **global** (the `kmptargets.targets` property, see below) — there is no
per-module selection block. A project-wide default for when `kmptargets.targets` is unset can be set
from build-logic via the `defaultSelection` property; a global `kmptargets.targets` always overrides
it.

### Seeing the DSL docs on hover (IntelliJ)

The plugin ships a sources jar, so every preset, leaf, and extension member carries KDoc you can
read on hover (`F1` / Quick Documentation). But `kmpTargets { … }` resolves through the **build-script
(plugin) classpath**, and IntelliJ does **not** attach sources for build-script dependencies by
default — so out of the box you'll only see the bare signature. Turn it on once:

1. **Settings → Advanced Settings → Build Tools. Gradle** → enable **"Attach scripts dependencies
   sources"** (also enable **"Download sources"**).
2. Reload the Gradle project (Gradle tool window → 🔄). A sync is required for the setting to take
   effect.

After the sync, hovering `supports`, `mobile`, `apple`, `iosArm64`, … shows their documentation.

### Build-logic conventions

The plugin ships just one extension (`KmpTargetsExtension`) and one set of public types
(`KmpTargetSet`, `KmpTarget`) — there are no bundled "preset" plugin ids. Teams that want
`apply by id, no body` ergonomics build their own conventions in `build-logic/`:

```kotlin
// build-logic/src/main/kotlin/my.mobile-module.gradle.kts
import com.rsicarelli.kmptargets.KmpTargetsExtension
import com.rsicarelli.kmptargets.model.KmpTargetSet

plugins {
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets")
}

extensions.configure<KmpTargetsExtension> {
    supports(KmpTargetSet.mobile)
}
```

```kotlin
// feature-mobile/build.gradle.kts
plugins { id("my.mobile-module") }
```

This is how a team centralizes per-module shape vocabulary in their own naming, instead of taking a
vocabulary baked into this plugin's artifact. The DSL stays the primitive; convention plugins are an
optional layer you own.

#### Querying what registered

Conventions routinely need to wire *per-target* things after registration — the canonical case is
KSP, where each target gets its own configuration (`kspIosArm64`, `kspIosSimulatorArm64`, …).
Without help, every convention re-derives family matching and name mangling by importing KGP
internals:

```kotlin
// Before — KGP konan types plus hand-rolled name mangling:
kotlin.targets.withType<KotlinNativeTarget>()
    .matching { it.konanTarget.family == Family.IOS }
    .forEach { add("ksp${it.targetName.replaceFirstChar(Char::titlecase)}", dep) }
```

The extension exposes the **registered** set directly (issue #52), so build-logic never touches
`konanTarget`:

```kotlin
// After — per-leaf hook with the actual Gradle name, pre-mangled:
kmpTargets.onRegistered { add("ksp${it.gradleNameCapitalized}", dep) }

// Snapshots, family-sliced with plain set algebra:
kmpTargets.registered()                          // everything registered so far, as KmpTargetSet
kmpTargets.registered(KmpTargetSet.appleMobile)  // just the registered iOS leaves
```

**Configuration-name helpers (issue #74).** Even the `"ksp${it.gradleNameCapitalized}"` form above
is a string to get wrong — and the test compilation's `kspJvmTest` is the one that bites: hardcode
it and it throws *Configuration 'kspJvmTest' not found* the moment a `targetName(jvm, "desktop")`
rename turns it into `kspDesktopTest`. `RegisteredTarget` owns that grammar so you never hand-roll
it:

```kotlin
kmpTargets.onRegistered {
    add(it.configurationName("ksp"), processor)      // kspJvm / kspIosArm64 / kspDesktop (renamed)
    add(it.testConfigurationName("ksp"), processor)  // kspJvmTest / … / kspDesktopTest (renamed)
}
```

Both are tool-generic — the prefix is any tool's convention (`it.configurationName("kapt")`); the
plugin blesses no single processor. Always wire through `onRegistered`, never an eager
`kotlin.targets` snapshot taken in the build script: that snapshot is ordering-sensitive (empty if
read before `supports { }`), whereas the callback's `configureEach` semantics are not.

**Callback vs snapshot.** `onRegistered` has `configureEach` semantics: hooked *before* the
module's `supports { }` it fires as each leaf registers; hooked *after*, it replays for the leaves
already registered — and a later `supports { }` union fires only its delta. That makes it the
ordering-immune choice for convention plugins. The `registered()` snapshot is the simple read for
build-script code that runs after `supports { }`; remember it is a point-in-time value — a later
`supports { }` call can union in more leaves.

Each `onRegistered` callback receives a `RegisteredTarget`: the model leaf plus the Gradle target
name registration **actually used** — a jvm leaf renamed via `targetName(jvm, "desktop")` reports
`gradleName == "desktop"`, so configuration wiring keeps working without the consumer knowing
about the rename. `registered()` deliberately mirrors real registrations, not the abstract
`resolvedSelection() ∩ resolvedSupported()` plan: without KGP applied it is empty (and
`onRegistered` never fires), and an `androidTarget` skipped because no Android Gradle plugin is
applied stays absent — so KSP wiring can never target a configuration that doesn't exist. Like
`supports`, the callback is a configuration-time tool: never hold it (or the extension) from a
task action.

The sample's [`kmpModule` convention](samples/hello-world/build-logic/src/main/kotlin/KmpModule.kt)
uses `onRegistered` as the canonical pattern.

### Configuration files

Global `kmp-targets` configuration lives in a **dedicated, committed root config file** —
`kmp-targets.properties` — so there is one discoverable place to see what the plugin is configured
to do, instead of loose keys buried among unrelated daemon/cache settings in `gradle.properties`:

```properties
# kmp-targets.properties — committed, team-shared
kmptargets.targets=jvm,iosArm64
kmptargets.hierarchyTemplate=true
# kmptargets.hierarchyCollapse=false    # opt-out of single-child collapse — see "Minimal hierarchy template"
# kmptargets.strict=true    # opt-in: advisories become failures — see "Strict mode"
# kmptargets.umbrellaTasks=true    # opt-in: register kmpCompileAll / kmpTestAll — see "Lane-agnostic compilation"
```

An optional **personal override file** — `kmp-targets.local.properties`, git-ignored — mirrors
Bazel's `try-import user.bazelrc`: absent it is ignored; present, its keys override the committed
file's. Both files accept only the known `kmptargets.*` keys — an unknown key **fails the build**
with a "did you mean …?" suggestion, so a typo can't silently no-op.

Both files are read as tracked configuration-cache inputs: editing one invalidates the cache,
leaving them untouched keeps cache hits.

### Selecting targets

The selection is **global** — one switch narrows the whole build. Its sources form **three layers
in increasing priority**, the same model as Bazel's `.bazelrc` + `try-import user.bazelrc`: a
committed team default, a git-ignored personal override, and a per-invocation flag.

| Layer | Sources | Who sets it |
|---|---|---|
| **Committed default** | `kmp-targets.properties` (and legacy root `gradle.properties`) | the team, in git |
| **Personal override** | `kmp-targets.local.properties` (and legacy `local.properties`) — git-ignored | each developer, per machine |
| **Per-invocation** | `-Pkmptargets.targets=…` CLI flag, `ORG_GRADLE_PROJECT_kmptargets.targets` env | this one build — a terminal run, a CI job |

`kmptargets.targets` is the **single canonical key** across every layer — the string you commit, the
string you override locally, and the string a [CI matrix](#ci) passes per job are the same
vocabulary. The exact resolution order within those layers (highest first):

1. `-Pkmptargets.targets=...` on the CLI
2. `ORG_GRADLE_PROJECT_kmptargets.targets` environment variable
3. `kmp-targets.local.properties` (per-developer, gitignored)
4. `kmp-targets.properties` (committed, team-shared)
5. the **root** `gradle.properties` (a *subproject's* `gradle.properties` is **not** a source —
   Gradle only reads root-level project properties)
6. `local.properties` (per-developer, gitignored)

When no source provides a value, two **fallbacks** (not overrides — anything above beats them)
apply: a project-wide `defaultSelection` set from build-logic, then the plugin default — every
target the plugin knows about.

The dedicated files beat `gradle.properties` deliberately: once a team adopts the consolidation
point, a stale key left behind in `gradle.properties` can't silently override it. One nuance: the
rarer `-Dorg.gradle.project.kmptargets.targets` and `~/.gradle/gradle.properties` forms resolve at
the `gradle.properties` layer, i.e. below the dedicated files.

> **Breaking rename (pre-1.0):** the selection key used to be `KMP_TARGETS` (env:
> `ORG_GRADLE_PROJECT_KMP_TARGETS` or bare `KMP_TARGETS`). It is now `kmptargets.targets` — the old
> key is **not read at all**; a build still setting only `KMP_TARGETS` falls through to
> `defaultSelection` / the plugin default. See [CHANGELOG.md](./CHANGELOG.md).

### Selection grammar

```properties
kmptargets.targets=android,iosArm64        # explicit list
kmptargets.targets=appleMobile             # preset (iosArm64 + iosSimulatorArm64 + iosX64)
kmptargets.targets=appleMobile,-iosArm64   # preset minus a leaf
kmptargets.targets=apple,+android          # preset plus an addition
kmptargets.targets=ANDROID, ios-arm64      # aliases + case-insensitive
```

Available presets:

| Preset | Expands to |
|---|---|
| `all` | every shipped target |
| `native` | every Kotlin/Native target (Apple + Linux + MinGW + Android Native) |
| `apple` | all Apple platforms: iOS + macOS + watchOS + tvOS |
| `appleMobile` / `appleDesktop` | all iOS / all macOS |
| `appleWatch` / `appleTv` | all watchOS / all tvOS |
| `linux` | `linuxX64`, `linuxArm64` |
| `mingw` (alias `windows`) | `mingwX64` |
| `androidNative` | the four `androidNative*` targets |
| `web` | `js`, `wasmJs`, `wasmWasi` |
| `jvmFamily` | `androidTarget`, `jvm` |
| `mobile` | `androidTarget` + all iOS |

Unknown tokens fail the build at configuration time with a "did you mean ...?" suggestion — silently dropping a misspelled target in CI is the worst failure mode, so the parser is strict. Bare Apple sub-family names (`ios`, `macos`, `watchos`, `tvos`) are rejected with a hint pointing at the relevant leaf or `appleX` preset.

### Selection change cost

Changing the selection changes which targets register, which changes what the configuration phase
produces. The price is exact and **expected**: switching to a new `kmptargets.targets` value costs
**one configuration-cache miss + one Gradle sync**. Each distinct value is its own
configuration-cache entry and the entries coexist, so switching *back* to a value you used before is
a cache **hit** again — alternating between two working sets re-configures nothing after the first
time each was seen.

There is **no file-watch workaround**, by design on both sides: Gradle has no mechanism that re-runs
the *configuration* phase on a file change — nothing can "hot reload" a selection change — and this
plugin will not fake one. The miss is the price of *not registering unused targets*, the same
trade-off that makes the plugin worthwhile: you pay one re-configuration when you narrow, and every
sync and build after that is smaller because the unused targets' tasks no longer exist. With the
configuration cache on (Gradle 9.5 runs it by default; this repo also pins it in
`gradle.properties`) the cost is bounded to exactly that one re-configuration per new value.

If a selection change seems to behave unexpectedly, run [`kmpTargetsInfo`](#debugging-the-selection)
— it prints what resolved and which source won, at configuration-cache-hit speed.

### Debugging the selection

Every project the plugin is applied to gets a `kmpTargetsInfo` task (group `help`) that answers
"what did the plugin decide for this module, and why?" — the resolved selection, **which
[source](#selecting-targets) it came from**, the declared supported set, the registered
intersection, and the vocabulary the parser accepts:

```console
$ ./gradlew :shared-core:kmpTargetsInfo -q

kmp-targets — :shared-core

Selection (what to build now)
  targets:  iosArm64, iosSimulatorArm64, jvm
  source:   kmp-targets.properties

Supported (what this module can build)
  declared: yes
  targets:  androidNativeArm32, ..., watchosX64

Registered (selection ∩ supported)
  targets:  iosArm64, iosSimulatorArm64, jvm

Vocabulary
  presets:  all, native, appleMobile, appleDesktop, appleWatch, appleTv, apple, linux, mingw, windows, androidNative, web, jvmFamily, mobile
  leaves:   androidNativeArm32, ..., macosX64 (deprecated), ..., watchosX64 (deprecated) (25)
```

The `source` line names the winning layer of the [precedence chain](#selecting-targets) — e.g.
`command line (-Pkmptargets.targets)`, `kmp-targets.local.properties`, `local.properties`, or the
`defaultSelection` / built-in fallbacks. One coarseness, stated in the label itself: values from
`-Dorg.gradle.project.kmptargets.targets` and `~/.gradle/gradle.properties` are indistinguishable
from root `gradle.properties`, so all three report as the fused `gradle.properties (...)` layer.

The report is read-only (it never registers targets), explicit about every empty case — a module
that never declared `supports { … }`, a selection narrowed to nothing, or a genuinely disjoint
`selection ∩ supported` each get their own explanation — and configuration-cache compatible: the
second run is a cache hit.

Two per-leaf annotations surface what the lists alone can't say: registered leaves the **current
host cannot compile** are marked `iosArm64 (not compilable on this host: LINUX_X64)` (same decision
source as the [host compatibility](#host-compatibility) advisory), and vocabulary leaves Kotlin
[marks deprecated](https://kotlinlang.org/docs/native-target-support.html) carry `(deprecated)` —
so the copy-paste surface itself tells you what to avoid adopting.

### Supported targets

Every target Kotlin Multiplatform (KGP 2.3.21) supports, except the deprecated `linuxArm32Hfp`:

| Family | Targets |
|---|---|
| JVM | `androidTarget` (alias `android`), `jvm` (alias `desktop`) |
| iOS | `iosArm64`, `iosSimulatorArm64`, `iosX64` |
| macOS | `macosArm64`, `macosX64` |
| watchOS | `watchosArm64`, `watchosArm32`, `watchosX64`, `watchosSimulatorArm64`, `watchosDeviceArm64` |
| tvOS | `tvosArm64`, `tvosX64`, `tvosSimulatorArm64` |
| Linux | `linuxX64`, `linuxArm64` |
| MinGW | `mingwX64` |
| Android Native | `androidNativeArm32`, `androidNativeArm64`, `androidNativeX86`, `androidNativeX64` |
| Web | `js`, `wasmJs`, `wasmWasi` |

Every leaf also accepts a kebab-case alias (e.g. `watchos-sim-arm64`, `linux-x64`, `android-native-arm64`).

### Renaming the jvm target

Codebases that grew up before the default-hierarchy era often register their JVM target under a
custom name — most commonly `kotlin.jvm("desktop")` — and carry years of accumulated
`src/desktopMain` source dirs, `-desktop` published artifact suffixes, and CI task names
(`compileKotlinDesktop`) keyed off it. For them, adopting kmp-targets must not be a breaking rename.
`targetName` lets the jvm leaf register under that custom Gradle name:

```kotlin
kmpTargets {
    targetName(jvm, "desktop") // must come BEFORE supports { } — registration is eager and one-way
    supports { mobile + jvm }
}
```

- **Selection token unchanged**: builds still select it with `jvm` (or its `desktop` alias) —
  `kmptargets.targets=jvm,iosArm64` registers the renamed target. Only the registered Gradle
  target, its source sets (`desktopMain`), and the published artifact suffix follow the custom
  name.
- **jvm leaf only**: `androidTarget`'s name is fixed by AGP, and native/web names are derived by
  KGP — renaming them would break konan/source-set conventions. `targetName` fails loud on any
  other leaf (or a preset), on a blank name, and when called after `supports { }` already
  registered the jvm leaf (a late rename could never apply).
- **Hierarchy unaffected**: KGP's hierarchy matchers (`withJvm()`) key off the platform type, not
  the target name, so the minimal template attaches a renamed target exactly as it would a plain
  `jvm`.
- `kmpTargetsInfo` surfaces the rename on the registered line: `jvm (registered as: desktop)`.

### Minimal hierarchy template

KGP's auto-applied `applyDefaultHierarchyTemplate()` builds the *full* source-set hierarchy for all
possible targets, so an iOS-only module still gets `nativeMain` **and** `appleMain` intermediate
source sets that are redundant with `iosMain`. Each redundant intermediate spawns ~8 wasteful Gradle
tasks; across dozens of modules this dominates sync time (see
[the cost of default hierarchy templates](https://dev.to/rsicarelli/the-hidden-cost-of-default-hierarchy-templates-in-kotlin-multiplatform-256a)).

Because `kmp-targets` already knows each module's active target set, it applies a **minimal** custom
hierarchy instead — collapsing every redundant single-child group:

| Active targets | Intermediate source sets |
|---|---|
| one iOS leaf | none (target attaches to `commonMain`) |
| iOS (≥2 leaves) | `iosMain` only — no `appleMain`, no `nativeMain` |
| iOS + macOS | `appleMain` over `iosMain` + `macosMain` — no `nativeMain` |
| iOS + Linux | `nativeMain` over `iosMain` + `linuxMain` — no `appleMain` |

It's **on by default** and applied automatically — no configuration needed. To opt out (and let KGP
apply its own default), set the global key or override per project (project wins):

```properties
# kmp-targets.properties — global default (also accepted via -P, env, gradle.properties,
# local.properties — same precedence chain as kmptargets.targets)
kmptargets.hierarchyTemplate=false
```

```kotlin
// any module's build.gradle.kts — per-project override
kmpTargets { hierarchyTemplate.set(false) }
```

Precedence is **project DSL > global key > built-in default (`true`)**. Opt out when a module
supplies its own `applyHierarchyTemplate { … }`, so the plugin stays out of the way.

#### Keeping intermediates: no-collapse mode

The collapse rule above is what keeps the tree minimal: a group materializes a source set only when
it merges **≥2** present children; a single-child group collapses away. That is the right default
for new code, but established codebases have **load-bearing intermediate source dirs** — dozens of
modules with `src/iosMain` holding `actual` implementations. For them, narrowing the selection to a
single iOS leaf (`-Pkmptargets.targets=iosArm64`, the everyday "build for device only" move)
silently drops `iosMain` from the model and the build breaks with unresolved `expect` declarations.

The opt-out is the **no-collapse** mode: a group materializes whenever it has **≥1** present child,
so `iosMain` survives a single-iOS-leaf selection. Single-child chains materialize fully
(`nativeMain → appleMain → iosMain` for one iOS leaf) — the inert empty intermediates are harmless
(no code, no `expect`/`actual` to resolve). Empty groups are still dropped, and ungrouped leaves
(jvm/android/web) still never form groups.

```properties
# kmp-targets.properties — global default (same precedence chain as the other keys)
kmptargets.hierarchyCollapse=false
```

```kotlin
// any module's build.gradle.kts — per-project override; set BEFORE supports { }
kmpTargets {
    collapseHierarchy.set(false)
    supports { appleMobile }
}
```

Precedence mirrors `hierarchyTemplate`: **project DSL > global key > built-in default (`true`,
collapse — the minimal tree)**. The knob never changes **what registers** — only which intermediate
source sets materialize — and it is a documented no-op when `hierarchyTemplate` resolves to `false`
(KGP's default template owns the tree then). A possible future extension — force-materializing
*named* groups only (e.g. just `ios`) to avoid the inert parents — is out of scope for now.

### Host compatibility

The registered target set is **host-blind by design**: selecting `iosArm64` registers `iosArm64` on
macOS, Linux, and Windows alike, so configuration-cache keys, task graphs, and published metadata
stay identical across CI agents. But not every host can *compile* every native target — `iosArm64`
needs a macOS host (the full host → target matrix lives on Kotlin's
[native target support](https://kotlinlang.org/docs/native-target-support.html) page; the plugin
reads KGP's own encoding of it, so the advisory below tracks your Kotlin version automatically).

When the selection includes a native target the current host cannot compile, the plugin logs a
configuration-time **warning** naming the target(s) and the host (e.g. `LINUX_X64`) — and **still
registers them**. Nothing is silently dropped; your explicit selection is honored deterministically,
and Kotlin/Native's compile/link tasks for those targets simply won't succeed on that host:

```
kmp-targets: ':shared' selects [iosArm64] which cannot be compiled on this host (LINUX_X64) —
still registered (selection is host-independent), but compile/link tasks for them will fail or
be skipped here.
```

The warning is purely advisory and fires at most once per target per module. JVM and Web targets
(`androidTarget`, `jvm`, `js`, `wasmJs`, `wasmWasi`) are host-agnostic and never warned.

### Deprecated targets

Kotlin's [target-support page](https://kotlinlang.org/docs/native-target-support.html) marks
`macosX64`, `watchosX64`, and `tvosX64` **deprecated** (since Kotlin 2.3.20) — but KGP itself emits
no configuration-time signal when they register. This plugin does: registering a deprecated leaf
logs a one-line advisory naming it —

```
kmp-targets: ':shared' registers [macosX64] which Kotlin marks deprecated (since Kotlin 2.3.20,
see https://kotlinlang.org/docs/native-target-support.html) — still registered (selection is
unchanged), but consider migrating off them.
```

Like the host advisory: **signal only, never filtering**. Deprecated leaves stay selectable, stay in
every preset (`appleDesktop` still expands to both macOS leaves), and register exactly as selected;
the advisory fires at most once per leaf per module and only for leaves that actually register.
[`kmpTargetsInfo`](#debugging-the-selection) marks the same leaves `(deprecated)` in its vocabulary
listing. (`iosX64` is low-tier but *not* deprecated — it carries no marker.) The set is pinned in
the plugin against the docs page and updated deliberately with Kotlin upgrades.

### Android target without the Android Gradle plugin

`androidTarget` is the one leaf whose registration needs more than KGP: `kotlin.androidTarget()`
is a **hard KGP failure** (the FATAL `AndroidGradlePluginIsMissing` diagnostic) unless an Android
Gradle plugin — `com.android.library` or `com.android.application` (or any other id KGP accepts:
`dynamic-feature`, `test`, …) — is already applied to the module. So when a module both selects
and supports `androidTarget` but no Android plugin is applied by the time `supports { }` runs, the
plugin **skips registering that leaf** and logs a one-line advisory naming the module and the fix:

```
kmp-targets: ':feature' selects and supports [androidTarget] but no Android Gradle plugin is
applied — the target was not registered. Apply com.android.library or com.android.application
before supports { }.
```

This is the one advisory that **does filter**: unlike its siblings, the flagged leaf genuinely does
not register — the alternative is KGP's raw crash with no module-level guidance, which during
build-logic migrations reads as "kmp-targets dropped my target". The skip happens in both modes;
[strict mode](#strict-mode) only changes the severity of the signal. Everything else in the active
set registers exactly as selected, and the advisory fires at most once per module.

The fix is an **ordering rule**: apply the Android plugin *before* `supports { }`. A convention
plugin that applies AGP after `supports { }` has already missed registration — though a later
`supports { }` union re-checks, so AGP applied between two calls lets the later pass register the
leaf normally. [`kmpTargetsInfo`](#debugging-the-selection) marks the leaf
`androidTarget (skipped: no Android plugin applied)` in its registered section while the condition
holds.

### Inert modules

A module that declared `supports { … }` can still end up registering **zero** targets — that is
explicit selection working as intended, and it happens three ways: the selection is disjoint from
the supported set (the everyday narrowed-lane case), the selection is explicitly empty
(`kmptargets.targets=jvm,-jvm` — the honored "build nothing" of the
[selection grammar](#selection-grammar)), or the only overlap was an `androidTarget` that the
[AGP guard](#android-target-without-the-android-gradle-plugin) skipped. The trap: KGP still
materializes the **commonMain metadata compilation** (`compileCommonMainKotlinMetadata`) for every
module that applies `kotlin("multiplatform")`, and on a module with no platform targets that
compilation **fails** — a platform-less commonMain has no legal platform context, so declarations
relying on `@OptionalExpectation` (and friends) are rejected. Any aggregate invocation (`build`,
`check`, `publishToMavenLocal`) then trips over every inert module with an opaque compiler error.

So whenever a registration pass leaves such a module with zero registrations, the plugin logs a
one-line advisory naming the consequence and the gate:

```
kmp-targets: ':shared' declared supports { } but registered zero targets — the module is inert.
KGP still materializes the commonMain metadata compilation, which fails with no platform targets.
Gate it in build-logic when kmpTargets.registered().isEmpty().
```

Like its siblings (and unlike the AGP guard): **signal only, never filtering** — the plugin does
not disable any task. The recommended gate lives in your build-logic, right after the module's
`supports { }` (or in a convention plugin), keyed off the same
[`registered()`](#querying-what-registered) query the advisory checks:

```kotlin
// after supports { } — disable the doomed compilations when nothing registered
if (kmpTargets.registered().isEmpty()) {
    tasks.withType(KotlinCompilationTask::class.java).configureEach { enabled = false }
}
```

A module that **never calls** `supports { }` registers nothing *by definition* and is deliberately
not flagged — that is the explicit-selection baseline, not the trap. The advisory fires at most
once per module; a later `supports { }` union that registers a leaf un-inerts the module
permanently (registration is one-way). It is host-blind like registration itself: a registered
target the current host [cannot compile](#host-compatibility) still counts as registered.
[`kmpTargetsInfo`](#debugging-the-selection) renders the same decision as an `inert:` line in its
registered section while the condition holds.

### Native-only metadata (JVM-less commonMain)

The [inert trap](#inert-modules) has a subtler sibling: a module that supports the JVM family
(`androidTarget`, `jvm`) **plus** native targets, under a selection that drops every JVM-family
leaf (an ios-only lane, say). The module is very much *alive* — the native leaves register and
their platform klibs compile cleanly — but commonMain is now a **JVM-less shared fragment**, and
the metadata compiler applies shared-fragment rules to code that was written assuming a JVM
platform exists: `@JvmInline` and similar JVM-flavored constructs are rejected — but **only** in
the `*KotlinMetadata*` compilations. The platform compilations succeed, which makes the failure
look paradoxical ("the klibs build, why does metadata fail?"). The same holds when only web leaves
register: the predicate is "no JVM-providing leaf", not "natives registered".

So whenever a registration pass leaves such a module with targets registered but none of them
JVM-family — while the supported set says the JVM family belongs here — the plugin logs a one-line
advisory naming the symptom and the scoped gate:

```
kmp-targets: ':shared' supports the JVM family but this selection registered no JVM-family target
while other targets did — commonMain is now a JVM-less shared fragment. The platform klibs
compile, but the *KotlinMetadata* compilations reject JVM-flavored constructs (e.g. @JvmInline):
klibs build, metadata fails. Gate it in build-logic when kmpTargets.registered(jvmFamily).isEmpty(),
disabling only the *KotlinMetadata* compilations.
```

Like its siblings: **signal only, never filtering** — the plugin does not disable any task. The
recommended gate is the *scoped* sibling of the [inert recipe](#inert-modules): where an inert
module disables everything, this module keeps its alive platform compilations and drops only the
doomed metadata ones, keyed off the same
[`registered(slice)`](#querying-what-registered) query the advisory checks:

```kotlin
// after supports { } — drop the doomed metadata compilations, keep the platform klibs
if (kmpTargets.registered().isNotEmpty() && kmpTargets.registered(jvmFamily).isEmpty()) {
    tasks.withType(KotlinCompilationTask::class.java)
        .matching { it.name.contains("KotlinMetadata") }
        .configureEach { enabled = false }
}
```

The predicate is precise on both sides. *Supported* means `resolvedSupported()` — so an
`androidTarget` that was selected but [skipped for want of AGP](#android-target-without-the-android-gradle-plugin)
still counts as "supports JVM, registered none", and in that composition both advisories fire:
the AGP guard names the cause, this one names the consequence (under
[strict](#strict-mode), the cause wins the exception). *Registered* means actual registrations,
leaf-based — any single JVM-family leaf defeats the predicate (an android-only registration keeps
the fragment JVM-flavored; Android is a JVM platform), and a jvm leaf
[renamed](#renaming-the-jvm-target) via `targetName` still counts. A module whose `supports { }`
never names a JVM-family leaf made no JVM promise and is never flagged; a module that registered
**zero** targets is the [inert](#inert-modules) case — the two advisories are mutually exclusive
by construction, so exactly one fires per module state.

The advisory fires at most once per module; a later `supports { }` union that registers a
JVM-family leaf resolves the trap permanently (registration is one-way). It is host-blind like
registration itself. [`kmpTargetsInfo`](#debugging-the-selection) renders the same decision as a
`jvm-less:` line in its registered section while the condition holds.

### Strict mode

By default all six advisories — the **empty-overlap** warning (a non-empty selection that matches
nothing a module `supports`, so the module registers zero targets), the
[**android-without-AGP**](#android-target-without-the-android-gradle-plugin) advisory, the
[**host-impossible**](#host-compatibility) warning, the
[**deprecated-target**](#deprecated-targets) advisory, the
[**inert-module**](#inert-modules) advisory, and the
[**native-only-metadata**](#native-only-metadata-jvm-less-commonmain) advisory — are just that:
warnings. Right for local
iteration, easy to miss in CI, where a module silently building nothing is usually a real
configuration bug.

`kmptargets.strict=true` promotes those advisories to configuration-time **build
failures** (`GradleException`) with the **identical message text** — severity changes, policy does
not. It never changes *which* configurations are flagged and never changes *what registers* (the
[android-without-AGP](#android-target-without-the-android-gradle-plugin) skip happens with or
without strict — the flag only escalates its signal). Default **off**, deliberately. The flag resolves through the same
[precedence chain](#selecting-targets) as every `kmptargets.*` key; anything other than
`true`/`false` (case-insensitive) is treated as unset, i.e. off.

The recommended setup keeps local builds advisory and turns CI strict:

```yaml
# CI only — e.g. a GitHub Actions env block
env:
  ORG_GRADLE_PROJECT_kmptargets.strict: "true"   # or: ./gradlew build -Pkmptargets.strict=true
```

> **Heads-up for cross-host CI:** with strict on, a selection that includes targets the agent
> cannot compile (e.g. iOS leaves on a Linux runner) fails the build by design. Strict CI pairs
> with per-host selections — see [CI](#ci) for the matrix pattern.

> **Heads-up for explicitly-empty selections:** with strict on, a selection narrowed to nothing
> (`kmptargets.targets=jvm,-jvm`) makes the [inert-module](#inert-modules) advisory fail **every**
> module that declares `supports { }` at configuration time, by design — the other advisories stay
> silent there, but every one of those modules carries a doomed metadata compilation. A
> build-nothing lane that really wants to configure successfully should not pair the empty
> selection with strict.

## CI

Kotlin Multiplatform CI has a sharp cost asymmetry: on **private repositories**, GitHub Actions
macOS runners bill at roughly **10x** the Ubuntu per-minute rate (public repos get standard runners
free), and Apple/native compilation — the part that *requires* macOS — is the dominant slice of
total CI time. The cost-aware, idiomatic pattern is an **OS × selection matrix**: each runner
compiles only the subset of targets it can host, instead of every runner attempting (or wastefully
cross-compiling) the full set.

`kmp-targets` is purpose-built for this. The matrix passes a host-appropriate selection per job —
and the values are **literally the same strings** a developer puts in `kmp-targets.local.properties`
or `-Pkmptargets.targets` locally (see [Selecting targets](#selecting-targets) and the
[Selection grammar](#selection-grammar)). CI never grows its own divergent selection language.

```yaml
name: Build

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    strategy:
      fail-fast: false
      matrix:
        include:
          - os: ubuntu-latest
            targets: jvm,android,web,linuxX64,linuxArm64,androidNative
          - os: macos-latest
            targets: apple
          - os: windows-latest
            targets: windows
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '23'
      - uses: gradle/actions/setup-gradle@v6
      # Quoted on purpose: PowerShell (windows-latest) splits unquoted commas into an array.
      - run: ./gradlew build "-Pkmptargets.targets=${{ matrix.targets }}"
```

Host → target mapping:

| Runner | `kmptargets.targets` | Rationale |
|---|---|---|
| `ubuntu-latest` | `jvm,android,js,wasmJs,wasmWasi,linuxX64,linuxArm64,androidNative` | the cheap runner — every non-Apple, non-MinGW target cross-compiles here (`android` requires AGP in your build; `web` is the `js,wasmJs,wasmWasi` preset) |
| `macos-latest` | `apple` (or `appleMobile` on PRs) | the only host that can compile/link Apple targets; keep the expensive runner's set minimal |
| `windows-latest` | `mingwX64` (alias `windows`) | the only host for MinGW |

Notes:

- **Same vocabulary, no drift.** Matrix values are plain [selection grammar](#selection-grammar)
  strings — presets, leaves, and `-` exclusions all work. What CI builds is what
  `kmpTargetsInfo` ([Debugging the selection](#debugging-the-selection)) prints locally for the
  same value.
- **Env form.** `ORG_GRADLE_PROJECT_kmptargets.targets` as a job `env:` is equivalent (dotted env
  keys work on GitHub Actions); `-P` is the canonical recommendation. Both sit above the committed
  `kmp-targets.properties` in [precedence](#selecting-targets), so a per-job value always wins
  over the repo default.
- **Cost tier (optional).** Restrict the macOS job to `appleMobile` on `pull_request` and run the
  full `apple` preset only on `push` to `main` — the matrix `targets` value is the only thing that
  changes. Label-gated jobs are a further refinement, not covered here.
- **Strict mode pairs well.** Set [`kmptargets.strict=true`](#strict-mode) on jobs whose selection
  the host can fully compile — a misconfigured module then fails the job instead of silently
  building nothing.
- **Configuration cache.** Each distinct selection is a distinct configuration-cache key; matrix
  jobs with different selections won't share an entry. Expected, not a regression — the same
  bounded cost documented in [Selection change cost](#selection-change-cost).
- **Toolchain parity.** JDK via `setup-java` (Temurin 23) matches the repo's mise pin
  (`.mise.toml`); Gradle comes from the committed wrapper (9.5.1) in both places — don't pin a
  different version in CI.

This repo runs the pattern for real:
[`.github/workflows/sample-matrix.yml`](./.github/workflows/sample-matrix.yml) builds the
hello-world sample per host — the macOS job is the only place Apple targets get genuinely
compiled — so the example above can't rot.

### Lane-agnostic compilation

A matrix that narrows the selection per job breaks the other half-truth in most CI: a **hardcoded
compile-task list**. Teams commonly drive compile-only jobs with explicit task names:

```bash
# Brittle: a snapshot of one particular selection.
./gradlew compileCommonMainKotlinMetadata compileKotlinJvm compileReleaseKotlinAndroid compileKotlinIosArm64
```

Once the selection is variable, that list has two failure modes:

- **Hard 404** — a name that exists in *no* module under the current lane (`compileReleaseKotlinAndroid`
  on an android-less selection) → `Task '…' not found`, the job fails before it starts.
- **Silent false-green (worse)** — under a [renamed jvm leaf](#renaming-the-jvm-target)
  (`targetName(jvm, "desktop")`) the real task is `compileKotlinDesktop`, so a literal
  `compileKotlinJvm` matches *zero* tasks and the job passes **while compiling nothing**. The test
  side is higher-stakes still: a hardcoded `jvmTest` never runs.

The plugin already knows exactly what registered in every module, so it can expose two per-module
umbrella lifecycle tasks that depend on **exactly the registered intersection** — selection-agnostic
and rename-proof:

- **`kmpCompileAll`** — compiles every registered target's main compilation.
- **`kmpTestAll`** — runs every registered target's tests (the targets that have a test task; a
  device-only native like `iosArm64` has none and is skipped).

They are **opt-in** — set `kmptargets.umbrellaTasks=true` (default off) to register them, since they
add dependency edges to every registered target's compile/test tasks and most builds only want them
for CI. The flag reads through the same [precedence chain](#selecting-targets) as every other
`kmptargets.` key (`-P`, env, `kmp-targets(.local).properties`, `gradle.properties`):

```properties
# kmp-targets.properties
kmptargets.umbrellaTasks=true
```

```bash
# Stable: one name, correct in every lane and under any rename.
./gradlew kmpCompileAll "-Pkmptargets.targets=${{ matrix.targets }}"
```

Both are registered in **every** module (even one that never called `supports { }`), so an
unqualified `./gradlew kmpCompileAll` from the root **fans out** to every project that has the task —
no root aggregator, no cross-project wiring. Under a narrowed lane the umbrella simply depends on
fewer tasks (a module with nothing registered is a clean no-op — never a 404), and under a renamed
jvm leaf it wires the real `compileKotlinDesktop` / `desktopTest`.

They depend on registered **platform** compilations only and **never** on
`compileCommonMainKotlinMetadata` (or any `*KotlinMetadata` compilation), so they cannot re-introduce
the [inert-module](#inert-modules) (#71) or [JVM-less-fragment](#native-only-metadata-jvm-less-commonmain)
(#72) failures — exactly the compilations build-logic deliberately disables for those modules.

Drop them straight into the matrix: replace `build` with `kmpCompileAll` for compile-only jobs (or
add `kmpTestAll` where the host can run the lane's tests). The
[`desktop-named`](./samples/hello-world/desktop-named/build.gradle.kts) sample asserts the
rename-proofing as a living regression gate.

## Compatibility

The published jar targets the oldest supported consumer, not the toolchain this repo builds with:

| Consumer surface | Floor |
|---|---|
| `kotlin-dsl` build-logic (Gradle-embedded Kotlin) | Gradle 8.11+ / embedded Kotlin **2.0** |
| Daemon JVM | **JDK 17** |
| Kotlin Gradle Plugin at runtime | **KGP 2.2+** |

The repo itself builds with the latest mise-pinned JDK and Kotlin — there are **no Gradle
toolchains** (see [why toolchains are rarely a good
idea](https://jakewharton.com/gradle-toolchains-are-rarely-a-good-idea/)). Instead the build pins
the emitted output: Kotlin `languageVersion`/`apiVersion` 2.0 (metadata the embedded 8.x compiler
can read), `jvmTarget` 17 + `-Xjdk-release=17` / `--release 17` (bytecode JDK 17 daemons can load,
with accidental-new-API protection), and a `kotlin-stdlib` 2.0.21 POM dependency. A
`verifyCompatFloors` task inspects the built jar on every `check`, so the floors cannot silently
regress.

## Installation

The plugin is published to **Maven Central** — group `com.rsicarelli`, artifact
`kmp-targets-gradle-plugin`, plugin id `com.rsicarelli.kmptargets` (no Gradle Plugin Portal). With
`mavenCentral()` in `pluginManagement.repositories`:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}
```

Build-logic referencing the DSL types depends on the artifact directly:
`com.rsicarelli:kmp-targets-gradle-plugin:0.1.0-SNAPSHOT`.

Snapshots of every `main` push land at the Central Portal snapshots repository
(`https://central.sonatype.com/repository/maven-snapshots/`). Full instructions — version catalogs,
snapshot hygiene, prerequisites — live in the docs:
**[rsicarelli.github.io/kmp-targets](https://rsicarelli.github.io/kmp-targets/get-started/)**.

For working on the plugin itself:

```bash
task publish-local           # publishes the plugin to mavenLocal
task sample                  # smoke test against the multi-module samples/hello-world
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
