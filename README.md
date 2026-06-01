# kmp-targets

> Dynamically select which Kotlin Multiplatform targets to build.

**Status:** alpha (pre-1.0). The core selector and the automatic minimal hierarchy template are implemented and exercised by the multi-module sample; further helper APIs (user-defined hierarchy groups, XCFramework) are roadmap items.

`kmp-targets` is a Gradle plugin for Kotlin Multiplatform projects that lets each developer (and each CI runner) choose which KMP targets to build, via a single Gradle property:

```properties
# local.properties, gradle.properties, or -P
KMP_TARGETS=jvm,iosArm64
```

Targets you don't select are never registered with KGP, so their compile/link/KSP/publish tasks never run — cutting Gradle sync and build times when you only need a subset.

## Usage

A real multi-module project is heterogeneous: a shared module builds every platform, a mobile
feature builds only Android + iOS, a tooling module builds only the JVM. `kmp-targets` separates two
facts:

- **What a module *can* build** — its *supported* set, declared per-module via the type-safe DSL.
- **What you *want* to build now** — the global `KMP_TARGETS` *selection*.

The plugin registers `selection ∩ supported` for each module. A module never builds a target outside
its supported set, and the global selection narrows that further.

### Applying the plugin

Apply KGP and the `com.rsicarelli.kmptargets` base id together, then declare the module's supported
set with the type-safe `kmpTargets { supported { … } }` DSL — the whole target vocabulary is in scope
as set algebra (`+` / `-`), no imports, no strings:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.3.21"
    id("com.rsicarelli.kmptargets") version "<version>"
}

kmpTargets {
    supported { mobile + web - iosX64 }     // presets and leaves compose freely
}
```

Every preset (`all`, `mobile`, `apple`, `web`, `jvmFamily`, …) and every leaf (`jvm`, `iosArm64`,
`linuxX64`, …) is available inside the block. Build-logic and consumers needing a raw value can use
the overload: `supported(KmpTargetSet.mobile + KmpTargetSet.web)`.

If you omit the `supported` block, the module defaults to **all** targets — the global
`KMP_TARGETS` alone decides what registers. That's the right default for a generic library that
wants to build whatever the developer is currently targeting.

You can also set a per-module **default** selection with `selection { … }`; a global `KMP_TARGETS`
(see below) always overrides it, so the global switch stays authoritative:

```kotlin
kmpTargets {
    supported { mobile + web }
    selection { jvm + iosArm64 }            // default when no global KMP_TARGETS is set
}
```

### Seeing the DSL docs on hover (IntelliJ)

The plugin ships a sources jar, so every preset, leaf, and extension member carries KDoc you can
read on hover (`F1` / Quick Documentation). But `kmpTargets { … }` resolves through the **build-script
(plugin) classpath**, and IntelliJ does **not** attach sources for build-script dependencies by
default — so out of the box you'll only see the bare signature. Turn it on once:

1. **Settings → Advanced Settings → Build Tools. Gradle** → enable **"Attach scripts dependencies
   sources"** (also enable **"Download sources"**).
2. Reload the Gradle project (Gradle tool window → 🔄). A sync is required for the setting to take
   effect.

After the sync, hovering `supported`, `mobile`, `apple`, `iosArm64`, … shows their documentation.

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
    supported(KmpTargetSet.mobile)
}
```

```kotlin
// feature-mobile/build.gradle.kts
plugins { id("my.mobile-module") }
```

This is how a team centralizes per-module shape vocabulary in their own naming, instead of taking a
vocabulary baked into this plugin's artifact. The DSL stays the primitive; convention plugins are an
optional layer you own.

### Selecting targets

The selection is **global** — one switch narrows the whole build. Set it via any of these sources
(priority order, highest first):

1. `-PKMP_TARGETS=...` on the CLI
2. `ORG_GRADLE_PROJECT_KMP_TARGETS` environment variable
3. the **root** `gradle.properties` (a *subproject's* `gradle.properties` is **not** a source —
   Gradle only reads root-level project properties)
4. `local.properties` (per-developer, gitignored)
5. a per-module `kmpTargets { selection { … } }` default (overridden by all of the above)
6. Plugin default — every target the plugin currently knows about

### Selection grammar

```properties
KMP_TARGETS=android,iosArm64        # explicit list
KMP_TARGETS=appleMobile             # preset (iosArm64 + iosSimulatorArm64)
KMP_TARGETS=appleMobile,-iosArm64   # preset minus a leaf
KMP_TARGETS=apple,+android          # preset plus an addition
KMP_TARGETS=ANDROID, ios-arm64      # aliases + case-insensitive
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
apply its own default), set the Gradle property globally or override per project (project wins):

```properties
# root gradle.properties — global default
kmptargets.hierarchyTemplate=false
```

```kotlin
// any module's build.gradle.kts — per-project override
kmpTargets { hierarchyTemplate.set(false) }
```

Precedence is **project DSL > global property > built-in default (`true`)**. Opt out when a module
supplies its own `applyHierarchyTemplate { … }`, so the plugin stays out of the way.

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
