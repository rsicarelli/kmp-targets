# Diagnostics

Two read-only tasks (group `help`) on every module the plugin is applied to: `kmpTargetsInfo` reports state — what resolved, what registered, and why. `kmpTargetsDoctor` reports findings — what's wrong, the consequence, and the fix.

## kmpTargetsInfo

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

### Reading the report

- **`Selection`** — the resolved global selection and its winning [layer](selection-layers.md) by name: `command line (-Pkmptargets.targets)`, `kmp-targets.local.properties`, `local.properties`, or the `defaultSelection` / built-in fallbacks. Values from `-Dorg.gradle.project.kmptargets.targets` and `~/.gradle/gradle.properties` report as the fused `gradle.properties (...)` layer.
- **`Supported`** — whether the module declared [`supports { }`](selection-dsl.md) and what it expanded to.
- **`Registered`** — the intersection that registered. Every empty case states its reason: no `supports { }` call, a selection narrowed to nothing, or a disjoint `selection ∩ supported`.
- **`Vocabulary`** — the parser's full preset and leaf list; a copy-paste surface for valid tokens.

### Per-leaf annotations

| Annotation | Meaning |
|---|---|
| `iosArm64 (not compilable on this host: LINUX_X64)` | registered, but this host can't compile it — see the [host advisory](advisories.md#host-compatibility) |
| `macosX64 (deprecated)` | Kotlin [marks the leaf deprecated](https://kotlinlang.org/docs/native-target-support.html) — see the [deprecated advisory](advisories.md#deprecated-targets) |
| `androidTarget (skipped: no Android plugin applied)` | the [AGP guard](advisories.md#android-target-without-agp) skipped it |
| `jvm (registered as: desktop)` | the leaf registered under a [custom name](selection-dsl.md#renaming-the-jvm-target) |
| `inert:` line | the module registered zero targets — the [inert-module](advisories.md#inert-modules) condition |

## kmpTargetsDoctor

```bash
./gradlew :feature-mobile:kmpTargetsDoctor -q
```

A healthy module:

```console
kmp-targets doctor — :jvm-tools

✓ clean bill of health — registered: jvm
```

A module that hit a trap gets one `[!]` block per finding:

```console
kmp-targets doctor — :jvm-tools

[!] selection matches nothing supported
    why:    the selection iosArm64 matches none of the supported set jvm
    effect: this module registers no targets
    fix:    widen the selection or this module's supports { } so they overlap

[!] inert module
    why:    declared supports { } but registered zero targets
    effect: KGP still materializes the commonMain metadata compilation, which fails with no platform targets
    fix:    gate on registered().isEmpty() in build-logic
```

### Findings

| Finding | Advisory it explains |
|---|---|
| `selection matches nothing supported` | [empty overlap](advisories.md#empty-overlap) |
| `inert module` | [inert modules](advisories.md#inert-modules) |
| `JVM-less commonMain` | [native-only metadata](advisories.md#native-only-metadata) |
| `androidTarget skipped` | [android without AGP](advisories.md#android-target-without-agp) |
| `not compilable on this host` | [host compatibility](advisories.md#host-compatibility) |
| `deprecated targets registered` | [deprecated targets](advisories.md#deprecated-targets) |
| `single-target KSP` | the [commonMain-KSP trap](recipes.md#commonmain-ksp-needs-two-targets) — doctor-only, no build-time advisory |
| `note: jvm (registered as: …)` | a [renamed target](selection-dsl.md#renaming-the-jvm-target) — context, not a problem |

`single-target KSP` is doctor-only: it fires when a KSP plugin is applied and exactly one target is registered, so the commonMain metadata route (`kspCommonMainKotlinMetadata`, which needs [≥2 targets](recipes.md#commonmain-ksp-needs-two-targets)) is absent. Whether the module *actually* generates into `commonMain` is unobservable, so the finding flags the risk rather than a certainty — and there is no build-time advisory or strict-mode escalation for it.

### Project-edge closure

Doctor also flags a `project(...)` dependency that registers none of the leaves this module needs (the failure in [Selection vs the dependency graph](recipes.md#selection-vs-the-dependency-graph)):

```console
kmp-targets doctor — :app-mobile

✓ clean bill of health — registered: iosArm64, jvm

Project-edge closure (project dependencies only)
[!] :app-mobile → :lib-jvm
    missing: iosArm64 — the dependency registers none of these leaves this module needs
    fix:     widen :lib-jvm's supports { } (or the selection) to register them
  limits: external (non-project) dependencies are invisible; the android→jvm fallback is approximate
```

The check crosses the project boundary by file, never by reading a peer project's model: each module emits its primitives via `kmpTargetsDoctorData` and the dependent resolves those files through a lenient artifact view. It is configuration-cache safe and runs unchanged under Isolated Projects.

Two permanent limits:

- **No external-dependency coverage.** Only `project(...)` edges are visible. The android→jvm-fallback pain usually comes from external `jvm`-only libraries; co-selecting `jvm` with `android` ([the rule](recipes.md#the-asymmetric-case-android-and-the-jvm-fallback)) remains the fix.
- **The android→jvm fallback is approximate.** Doctor assumes "an `androidTarget` consumer is satisfied by a `jvm`-only producer"; it does not reproduce Gradle's real attribute matching.

## Task properties

Both tasks are read-only (they never register targets), configuration-cache compatible (the second run is a cache hit), and per-module (`:feature-mobile:kmpTargetsDoctor`). Doctor runs even when the module is inert. Findings key off the same decisions the [advisories](advisories.md) use ([Design](../why-kmp-targets.md#diagnostics-philosophy)).
