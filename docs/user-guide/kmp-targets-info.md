# Inspecting with kmpTargetsInfo

Every project the plugin is applied to gets a `kmpTargetsInfo` task (group `help`) that answers *"what did the plugin decide for this module, and why?"*

```bash
./gradlew :shared-core:kmpTargetsInfo -q
```

```console
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

## Reading the report

**`Selection`** — the resolved global selection and, crucially, its **source**: the winning layer of the [precedence chain](selection-layers.md) by name — `command line (-Pkmptargets.targets)`, `kmp-targets.local.properties`, `local.properties`, or the `defaultSelection` / built-in fallbacks. One coarseness, stated in the label itself: values from `-Dorg.gradle.project.kmptargets.targets` and `~/.gradle/gradle.properties` are indistinguishable from root `gradle.properties`, so all three report as the fused `gradle.properties (...)` layer.

**`Supported`** — whether the module declared [`supports { }`](selection-dsl.md) and what it expanded to.

**`Registered`** — the intersection that actually registered. Every empty case gets its own explanation: a module that never declared `supports { … }`, a selection narrowed to nothing, or a genuinely disjoint `selection ∩ supported`.

**`Vocabulary`** — the parser's full preset and leaf list, i.e. a copy-paste surface for valid tokens.

## Per-leaf annotations

The lists carry markers the plain names can't:

| Annotation | Meaning |
|---|---|
| `iosArm64 (not compilable on this host: LINUX_X64)` | registered, but this host can't compile it — same decision source as the [host advisory](advisories.md#host-compatibility) |
| `macosX64 (deprecated)` | Kotlin [marks the leaf deprecated](https://kotlinlang.org/docs/native-target-support.html) — same set as the [deprecated advisory](advisories.md#deprecated-targets) |
| `androidTarget (skipped: no Android plugin applied)` | the [AGP guard](advisories.md#android-target-without-agp) skipped it |
| `jvm (registered as: desktop)` | the leaf registered under a [custom name](jvm-rename.md) |
| `inert:` line | the module registered zero targets — the [inert-module](advisories.md#inert-modules) condition |

## Properties of the task

- **Read-only** — it never registers targets; running it cannot change the build.
- **Configuration-cache compatible** — the second run is a cache hit.
- **Per-module** — run it on the module you're diagnosing (`:feature-mobile:kmpTargetsInfo`), not just the root.

!!! tip
    `kmpTargetsInfo` is the first move whenever a selection surprises you — it answers both "what won?" and "why is this module empty?" in one cached run.

## See also: doctor mode

`kmpTargetsInfo` is the neutral state dump. When a build actually *broke* and you want the cause, consequence, and fix in one place — plus a project-edge closure check across `project(...)` dependencies — reach for [`kmpTargetsDoctor`](doctor-mode.md).
