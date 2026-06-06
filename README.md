# kmp-targets

> Dynamically select which Kotlin Multiplatform targets to build.

**Status:** alpha (pre-1.0). Shipped and exercised by the multi-module sample plus a real 3-OS CI matrix: the global selector with [try-import-style layering](#selecting-targets) (dedicated config files, unified `kmptargets.*` keys), the automatic [minimal hierarchy template](#minimal-hierarchy-template), the [`kmpTargetsInfo`](#debugging-the-selection) introspection task, [host-compatibility](#host-compatibility) and [deprecated-target](#deprecated-targets) advisories, and opt-in [strict mode](#strict-mode). Roadmap: user-defined hierarchy groups, XCFramework helpers, Maven Central / Plugin Portal publishing.

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

### Configuration files

Global `kmp-targets` configuration lives in a **dedicated, committed root config file** —
`kmp-targets.properties` — so there is one discoverable place to see what the plugin is configured
to do, instead of loose keys buried among unrelated daemon/cache settings in `gradle.properties`:

```properties
# kmp-targets.properties — committed, team-shared
kmptargets.targets=jvm,iosArm64
kmptargets.hierarchyTemplate=true
# kmptargets.strict=true    # opt-in: advisories become failures — see "Strict mode"
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

### Strict mode

By default all three advisories — the **empty-overlap** warning (a non-empty selection that matches
nothing a module `supports`, so the module registers zero targets), the
[**host-impossible**](#host-compatibility) warning, and the
[**deprecated-target**](#deprecated-targets) advisory — are just that: warnings. Right for local
iteration, easy to miss in CI, where a module silently building nothing is usually a real
configuration bug.

`kmptargets.strict=true` promotes those advisories to configuration-time **build
failures** (`GradleException`) with the **identical message text** — severity changes, policy does
not. It never changes *which* configurations are flagged and never changes *what registers*.
Default **off**, deliberately. The flag resolves through the same
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

The plugin is not yet published to the Gradle Plugin Portal or Maven Central. Track progress in the issues / releases. Locally:

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
