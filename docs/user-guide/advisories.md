# Advisories & Strict Mode

The plugin emits five configuration-time advisories. Four are **signal only — never filtering**; one (android-without-AGP) genuinely skips a leaf because the alternative is a raw KGP crash. [Strict mode](#strict-mode) promotes all five to build failures with identical message text.

## Host compatibility

The registered set is **host-blind by design**: selecting `iosArm64` registers it on macOS, Linux, and Windows alike, so configuration-cache keys, task graphs, and published metadata stay identical across CI agents. But not every host can *compile* every native target. When the selection includes a native target the current host can't compile, the plugin warns — and **still registers it**:

```
kmp-targets: ':shared' selects [iosArm64] which cannot be compiled on this host (LINUX_X64) —
still registered (selection is host-independent), but compile/link tasks for them will fail or
be skipped here.
```

The host → target matrix comes from KGP's own encoding of [Kotlin's native target support](https://kotlinlang.org/docs/native-target-support.html), so it tracks your Kotlin version automatically. JVM and Web targets are host-agnostic and never warned. Fires at most once per target per module.

## Deprecated targets

Kotlin marks `macosX64`, `watchosX64`, and `tvosX64` deprecated (since Kotlin 2.3.20), but KGP emits no configuration-time signal when they register. This plugin does:

```
kmp-targets: ':shared' registers [macosX64] which Kotlin marks deprecated (since Kotlin 2.3.20,
see https://kotlinlang.org/docs/native-target-support.html) — still registered (selection is
unchanged), but consider migrating off them.
```

Signal only: deprecated leaves stay selectable, stay in every preset, and register exactly as selected. [`kmpTargetsInfo`](kmp-targets-info.md) marks the same leaves `(deprecated)` in its vocabulary listing. (`iosX64` is low-tier but *not* deprecated.)

## Android target without AGP

`androidTarget` is the one leaf whose registration needs more than KGP: `kotlin.androidTarget()` is a **hard KGP failure** (the FATAL `AndroidGradlePluginIsMissing` diagnostic) unless an Android Gradle plugin — `com.android.library`, `com.android.application`, or any other id KGP accepts — is already applied. So when a module both selects and supports `androidTarget` but no Android plugin is applied by the time `supports { }` runs, the plugin **skips registering that leaf** and says so:

```
kmp-targets: ':feature' selects and supports [androidTarget] but no Android Gradle plugin is
applied — the target was not registered. Apply com.android.library or com.android.application
before supports { }.
```

This is the one advisory that **does filter** — the alternative is KGP's raw crash with no module-level guidance, which during build-logic migrations reads as "kmp-targets dropped my target". The fix is an **ordering rule**: apply the Android plugin *before* `supports { }`. A later `supports { }` union re-checks, so AGP applied between two calls lets the later pass register the leaf normally. Everything else registers exactly as selected.

## Empty overlap

A non-empty selection that matches nothing a module `supports` (so the module registers zero targets) gets its own warning — the everyday symptom of a typo'd lane or an over-narrow selection.

## Inert modules

A module that declared `supports { … }` can still register **zero** targets — disjoint selection, explicitly empty selection (`kmptargets.targets=jvm,-jvm`), or the only overlap being an AGP-skipped `androidTarget`. The trap: KGP still materializes the **commonMain metadata compilation** for every module applying `kotlin("multiplatform")`, and with no platform targets that compilation **fails** — any aggregate invocation (`build`, `check`, `publishToMavenLocal`) then trips over every inert module with an opaque compiler error. So the plugin names the consequence and the gate:

```
kmp-targets: ':shared' declared supports { } but registered zero targets — the module is inert.
KGP still materializes the commonMain metadata compilation, which fails with no platform targets.
Gate it in build-logic when kmpTargets.registered().isEmpty().
```

The recommended gate lives in your build-logic, right after `supports { }`:

```kotlin
if (kmpTargets.registered().isEmpty()) {
    tasks.withType(KotlinCompilationTask::class.java).configureEach { enabled = false }
}
```

A module that **never calls** `supports { }` registers nothing *by definition* and is deliberately not flagged — that's the explicit-selection baseline, not the trap. A later `supports { }` union that registers a leaf un-inerts the module permanently.

## Strict mode

By default all five advisories are warnings — right for local iteration, easy to miss in CI, where a module silently building nothing is usually a real configuration bug.

`kmptargets.strict=true` promotes them to configuration-time **build failures** (`GradleException`) with the **identical message text** — severity changes, policy does not. It never changes *which* configurations are flagged and never changes *what registers* (the AGP skip happens with or without strict). Default **off**, deliberately. The flag resolves through the same [precedence chain](selection-layers.md) as every `kmptargets.*` key; anything other than `true`/`false` (case-insensitive) is treated as unset.

The recommended setup keeps local builds advisory and turns CI strict:

```yaml
# CI only — e.g. a GitHub Actions env block
env:
  ORG_GRADLE_PROJECT_kmptargets.strict: "true"   # or: ./gradlew build -Pkmptargets.strict=true
```

!!! warning "Cross-host CI"
    With strict on, a selection including targets the agent cannot compile (e.g. iOS leaves on a Linux runner) fails the build by design. Strict CI pairs with per-host selections — see [CI Matrix](ci-matrix.md).

!!! warning "Explicitly-empty selections"
    With strict on, a selection narrowed to nothing (`kmptargets.targets=jvm,-jvm`) makes the inert-module advisory fail **every** module that declares `supports { }`, by design — each carries a doomed metadata compilation. A build-nothing lane that wants to configure successfully should not pair the empty selection with strict.
