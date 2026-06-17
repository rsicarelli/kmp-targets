# Advisories & Strict Mode

The plugin emits eight configuration-time advisories. Seven are signal-only; one (android-without-AGP) skips a leaf — the reasoning is on the [Design page](../why-kmp-targets.md#diagnostics-philosophy). [Strict mode](#strict-mode) promotes all eight to build failures with identical message text. [`kmpTargetsDoctor`](diagnostics.md#kmptargetsdoctor) renders each advisory as a finding with the fix attached.

## Host compatibility

Fires when the selection includes a native target the current host can't compile. The target still registers ([host-blind registration](../why-kmp-targets.md#selection-model)); compile/link tasks for it fail or are skipped on this host:

```
kmp-targets: ':shared' selects [iosArm64] which cannot be compiled on this host (LINUX_X64) —
still registered (selection is host-independent), but compile/link tasks for them will fail or
be skipped here.
```

The host → target matrix comes from KGP's encoding of [Kotlin's native target support](https://kotlinlang.org/docs/native-target-support.html), so it tracks your Kotlin version. JVM and Web targets are host-agnostic and never warned. Fires at most once per target per module. Per-host builds: [CI](ci-matrix.md).

## Deprecated targets

Kotlin marks `macosX64`, `watchosX64`, and `tvosX64` deprecated (since 2.3.20) but emits no configuration-time signal when they register. This plugin does:

```
kmp-targets: ':shared' registers [macosX64] which Kotlin marks deprecated (since Kotlin 2.3.20,
see https://kotlinlang.org/docs/native-target-support.html) — still registered (selection is
unchanged), but consider migrating off them.
```

Deprecated leaves stay selectable, stay in every preset, and register as selected. [`kmpTargetsInfo`](diagnostics.md#kmptargetsinfo) marks them `(deprecated)`. `iosX64` is low-tier but not deprecated.

## Android target without AGP

`kotlin.androidTarget()` is a hard KGP failure unless an Android Gradle plugin is already applied. When a module selects and supports `androidTarget` but no Android plugin is applied by the time `supports { }` runs, the plugin skips that leaf:

```
kmp-targets: ':feature' selects and supports [androidTarget] but no Android Gradle plugin is
applied — the target was not registered. Apply com.android.library or com.android.application
before supports { } — gate it on the selection (see
https://rsicarelli.github.io/kmp-targets/user-guide/recipes/#selection-gated-eager-agp-application).
```

Fix: apply the Android plugin before `supports { }`. A later `supports { }` union re-checks, so AGP applied between two calls lets the later pass register the leaf. To apply AGP only on Android lanes, see [Selection-gated eager AGP application](recipes.md#selection-gated-eager-agp-application).

## Empty overlap

Fires when a non-empty selection matches nothing the module `supports` — the module registers zero targets. Typical cause: a typo'd lane or an over-narrow selection.

## Inert modules

A module that declared `supports { … }` can still register zero targets: disjoint selection, explicitly empty selection (`kmptargets.targets=jvm,-jvm`), or the only overlap being an AGP-skipped `androidTarget`. KGP materializes the commonMain metadata compilation even with zero platform targets, and it fails — every aggregate invocation (`build`, `check`) then trips over the module:

```
kmp-targets: ':shared' declared supports { } but registered zero targets — the module is inert.
KGP still materializes the commonMain metadata compilation, which fails with no platform targets.
Gate it in build-logic when kmpTargets.registered().isEmpty().
```

The gate, right after `supports { }`:

```kotlin
if (kmpTargets.registered().isEmpty()) {
    tasks.withType(KotlinCompilationTask::class.java).configureEach { enabled = false }
}
```

A module that never calls `supports { }` registers nothing by definition and is not flagged. A later `supports { }` union that registers a leaf un-inerts the module.

## Native-only metadata

Fires when a module supports the JVM family but, under this selection, registers no JVM-family leaf while registering at least one other target — `commonMain` becomes a JVM-less shared fragment:

```
kmp-targets: ':shared' supports the JVM family but this selection registered no JVM-family
target while other targets did — commonMain is now a JVM-less shared fragment. The platform
klibs compile, but the *KotlinMetadata* compilations reject JVM-flavored constructs (e.g.
@JvmInline): klibs build, metadata fails. Gate it in build-logic when
kmpTargets.registered(jvmFamily).isEmpty(), disabling only the *KotlinMetadata* compilations.
```

Platform klibs compile; the `*KotlinMetadata*` compilations reject JVM-flavored constructs such as `@JvmInline`. Cause-agnostic: a narrowed lane and an android-only [AGP skip](#android-target-without-agp) fire it alike. Disjoint from the inert advisory — inert means registered empty, this means registered non-empty with no JVM-family leaf; at most one fires per module. Any JVM-family leaf (`jvm` or `androidTarget`) defeats it.

The gate is the scoped sibling of inert's — disable only the metadata compilations, keep the platform ones:

```kotlin
// after supports { } — drop the doomed metadata compilations, keep the platform klibs
if (kmpTargets.registered().isNotEmpty() && kmpTargets.registered(jvmFamily).isEmpty()) {
    tasks.withType(KotlinCompilationTask::class.java)
        .matching { it.name.contains("KotlinMetadata") }
        .configureEach { enabled = false }
}
```

## Framework without an Apple target

Fires when a module declares an [`appleFramework`](apple-framework.md) but the current selection registers no Apple target in the framework's `on` scope — so the framework attaches to nothing and never builds. A jvm-only lane (`kmptargets.targets=jvm`) is the common cause; so is an `on` scope narrower than what registered (a framework scoped to `appleMobile` while only `macosArm64` registered). The artifact silently never materializes, and an Xcode build consuming it fails far downstream with a missing-framework error that says nothing about target selection:

```
kmp-targets: ':shared' declared appleFramework("KotlinShared") but this selection registered no
Apple target in its scope — the framework attaches to nothing and never builds, so an Xcode build
consuming it fails downstream with a missing-framework error. Widen the selection (or this module's
supports { }) to register an Apple target in the framework's `on` scope, or gate the appleFramework(…)
declaration in build-logic when kmpTargets.registered(apple).isEmpty().
```

Keyed off the leaves the framework **actually attached to** (`registered ∩ on ∩ apple`), not `registered(apple)` — so it is correct for an `on`-scoped framework even when other Apple leaves registered. A module that never calls `supports { }` is not flagged (explicit-selection doctrine), though [`kmpTargetsDoctor`](diagnostics.md#kmptargetsdoctor) still renders the declared-but-unattached state. Registration is one-way: a later `supports { }` union that attaches an Apple leaf resolves it permanently. Strict mode is the point here — a release lane with a misconfigured selection fails loudly at configuration instead of shipping a frameworkless artifact that breaks Xcode downstream.

## Framework build types disjoint

Fires when a framework attaches to ≥1 Apple leaf but the lane's [`kmptargets.framework.buildTypes`](apple-framework.md#build-types-per-lane) shares no `NativeBuildType` with what the module declared — so the effective set (`property ∩ declared`) is empty and no binary links, even though Apple targets registered. The build-type analog of [empty overlap](#empty-overlap) on the `selection ∩ supported` axis. Typical cause: a module pinned to one build type (`appleFramework("KotlinShared", buildTypes = listOf(NativeBuildType.DEBUG))`) under the opposite lane (`-Pkmptargets.framework.buildTypes=release`):

```
kmp-targets: ':shared' — appleFramework("KotlinShared") declares build types [DEBUG] but
kmptargets.framework.buildTypes resolved to [RELEASE] for this lane; they do not overlap, so no
framework binary (or XCFramework slice) links and an Xcode build consuming it fails downstream. Set
kmptargets.framework.buildTypes to one of [DEBUG] (or widen the appleFramework(…) declaration).
```

Mutually exclusive with [framework-without-an-Apple-target](#framework-without-an-apple-target) by construction: that one needs zero attached leaves, this one needs ≥1. Because the property only ever **narrows** the declaration, the fix is to align the lane with a declared build type (or widen the declaration). Fires at most once per module.

## Strict mode

`kmptargets.strict=true` promotes all eight advisories to configuration-time build failures (`GradleException`) with identical message text. Severity changes; policy does not — it never changes which configurations are flagged or what registers (the AGP skip happens with or without strict). Default off. Recommended: strict in CI only:

```yaml
# CI only — e.g. a GitHub Actions env block
env:
  ORG_GRADLE_PROJECT_kmptargets.strict: "true"   # or: ./gradlew build -Pkmptargets.strict=true
```

The flag resolves through the same [precedence chain](selection-layers.md) as every `kmptargets.*` key; anything other than `true`/`false` (case-insensitive) is treated as unset.

!!! warning "Cross-host CI"
    With strict on, a selection including targets the agent cannot compile fails the build by design. Pair strict CI with per-host selections — see [CI](ci-matrix.md).

!!! warning "Explicitly-empty selections"
    With strict on, a selection narrowed to nothing (`kmptargets.targets=jvm,-jvm`) makes the inert-module advisory fail every module that declares `supports { }`. A build-nothing lane should not pair the empty selection with strict.
