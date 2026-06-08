# Recipes

Build-logic patterns collected from real multi-module adoptions. Each is a few lines in a convention plugin; all of them key off [`registered()` / `onRegistered`](build-logic.md) rather than KGP internals.

## Gate compilation on inert modules

A narrowed lane can leave a module with zero registered targets — and KGP's commonMain metadata compilation [fails on a platform-less module](advisories.md#inert-modules). Gate it right after `supports { }`:

```kotlin
if (kmpTargets.registered().isEmpty()) {
    tasks.withType(KotlinCompilationTask::class.java).configureEach { enabled = false }
}
```

This is the gate the inert-module advisory names. Variant: when only a *native*-wired codegen processor is at risk rather than the whole module, gate on the narrower `kmpTargets.registered(KmpTargetSet.native).isEmpty()` — see [commonMain KSP needs a native target](#commonmain-ksp-needs-a-native-target).

## commonMain KSP needs a native target

A processor that generates into `commonMain` runs on the shared **commonMain metadata compilation** (`kspCommonMainKotlinMetadata`). KSP's multiplatform wiring only creates that task when the selection registers a **native** target — so a native-less lane (`jvm`, `android,jvm`) silently skips generation, and the failure ranges from unresolved-reference errors to a green build that ships missing codegen. There is **no per-platform workaround**: K2's strict fragment resolution forbids `commonMain` from referencing platform-generated symbols, so generating per-platform instead does not help. Gate the module — warn *and* disable the doomed compilations — right after `supports { }`:

```kotlin
if (kmpTargets.registered(KmpTargetSet.native).isEmpty()) {
    logger.warn(
        "ksp: ':${project.name}' generates commonMain code but no native target is " +
        "registered — symbol processing is skipped for this selection."
    )
    tasks.withType(KotlinCompilationTask::class.java).configureEach { enabled = false }
}
```

!!! note "Why the gate is `native`, not the metadata compilation itself"
    KGP's `compileCommonMainKotlinMetadata` tracks a *shared* commonMain (two or more platform targets), **not** native presence — `jvm + js` has it with zero native targets, a lone `iosArm64` does not. The **native** gate above is specific to the KSP commonMain-metadata *route*; it is the conservative predicate for a native-wired processor, not a claim about the compilation itself. [`kmpTargetsInfo`](kmp-targets-info.md) shows what registered, per lane.

## Per-target configuration wiring

The canonical `onRegistered` use: per-target configurations (KSP, custom attributes), with the Gradle name pre-mangled — and rename-proof, because `gradleNameCapitalized` follows [`targetName`](jvm-rename.md):

```kotlin
kmpTargets.onRegistered { add("ksp${it.gradleNameCapitalized}", processorDep) }
```

!!! warning "Never snapshot `kotlin.targets` eagerly"
    `kotlin.targets.forEach { … }` in a convention plugin sees only what registered *before that line ran* — ordering-sensitive and silently incomplete. `onRegistered` replays past registrations and fires for future ones.

## Selection-gated eager AGP application

`androidTarget` needs an Android Gradle plugin applied **before** `supports { }` ([the ordering rule](advisories.md#android-target-without-agp)) — `kotlin.androidTarget()` is a hard KGP FATAL without one. Applying AGP to *every* module is expensive (config-time cost plus a swarm of Android tasks/configurations that are meaningless under android-less lanes), so a **library** convention applies it only when Android is actually selected, read straight off [`resolvedSelection()`](build-logic.md):

```kotlin
// in the LIBRARY convention plugin, BEFORE the supports { } block
val androidSelected = KmpTarget.Jvm.Android in kmpTargets.resolvedSelection()
if (androidSelected) {
    pluginManager.apply("com.android.library")
    // configure AGP here, between apply and supports { } — also gated (see below)
    extensions.configure<com.android.build.api.dsl.LibraryExtension> {
        namespace = "com.example.${project.name}"
    }
}

kmpTargets.supports {
    androidTarget + jvm    // androidTarget registers iff AGP was applied above
}
```

The gate is `KmpTarget.Jvm.Android in resolvedSelection()`, not a preset membership test: `resolvedSelection()` is the same value registration intersects against, so the gate and the eventual registration never disagree.

!!! warning "Apply AGP before `supports { }`, never after"
    Registration is eager: `supports { }` checks for AGP *at that instant* and [skips `androidTarget`](advisories.md#android-target-without-agp) if it is absent. AGP applied **after** `supports { }` — or in a separate `afterEvaluate` — silently misses registration, and android never appears with no error outside [strict mode](advisories.md#strict-mode). Keep the `apply` above the block. (A later `supports { }` union re-checks, so AGP applied *between* two calls lets the later pass register the leaf — but the one-block form above is the rule.)

!!! warning "Gate downstream AGP reads with the SAME predicate"
    Any code that reads AGP-owned configuration — `android { namespace = … }`, `compileSdk`/manifest wiring, a `LibraryExtension` lookup — **crashes under a non-Android selection** if it runs unconditionally, because AGP was never applied in that lane. It must sit inside the same `if (androidSelected)` (as above) or be keyed off the same `resolvedSelection()` check. The half-gated state — AGP application gated, but a downstream `android { }` read left ungated — is where real-world partial-selection crashes come from. The gate is not just for the `apply` call; it is for everything that assumes AGP is present.

!!! note "`com.android.application` modules are exempt — do not gate them"
    An app module **is** Android by definition: AGP arrives from the app's own plugin block (or a `*.application` convention), not from this library gate. Do not apply `com.android.library` to it (double-applying AGP / half-configuring an app module breaks it), and do not put its AGP application behind the selection gate — the app always wants Android. This recipe is for **library** modules whose Android-ness is conditional on the lane.

!!! note "Companion plugins react via `pluginManager.withPlugin`"
    A plugin that must run only when AGP is present (the Gradle cache-fix plugin is the canonical case) should **react** to AGP rather than re-test the selection — it then stays correct whether AGP came from this gate or from an app's own block:

    ```kotlin
    pluginManager.withPlugin("com.android.library") {
        pluginManager.apply("org.gradle.android.cache-fix")
    }
    ```

Confirm per lane with [`kmpTargetsInfo`](kmp-targets-info.md): under a non-Android selection it shows no `androidTarget` and AGP is never applied; under an Android lane it shows `androidTarget` registered. There is deliberately **no plugin hook** for this — eager-gating AGP is a one-line `if` over `resolvedSelection()` (the primitive already exists), and the application-vs-library exemption is a build-logic decision the plugin cannot make for you ([#76](https://github.com/rsicarelli/kmp-targets/issues/76)).

## Lane-agnostic CI invocations

Aggregate tasks (`build`, `check`) only cover registered targets, so the **same Gradle invocation works in every lane** — the selection does the narrowing, not the task list:

```bash
./gradlew check "-Pkmptargets.targets=$LANE_TARGETS"
```

Avoid hardcoding per-target task names (`:m:iosArm64Test`) in CI scripts: they break under renames and lane changes. If you must derive test tasks, derive them from `registered()` in a small task-listing helper, not from string conventions.

## Selection vs the dependency graph

Selection is **per-module**, intersected per-module. If `:app` supports `jvm + iosArm64` and depends on `:lib` that only supports `jvm`, an `iosArm64` build of `:app` fails at *variant resolution* — `:lib` has no iOS variant to offer. The error is KGP's wall of text, but the cause is set arithmetic: **a module's supported set must cover every target its dependents build**. Check both sides with `kmpTargetsInfo` and widen `:lib`'s `supports { }` (or narrow `:app`'s).

### The asymmetric case: android and the jvm fallback

The same set arithmetic has a subtler form that bites at scale, and it is the single hardest selection failure to diagnose. An android-leaning module depends on a library that ships only `jvm` + native variants and **no `androidTarget`** — common for pure-Kotlin libraries that never opted into Android. That build still works, because Gradle resolves the android consumer against the library's **`jvm` variant** as a fallback. The android module was relying on the jvm variant all along, even though nobody wrote it down.

Now narrow the selection to pure `android` (`kmptargets.targets=android`). Every producer that *supported* `jvm` now registers no jvm target — and the jvm variant the android consumers were falling back to is gone. The edge no longer resolves. The rule:

> **An android consumer can be satisfied by a producer's `jvm` variant (the jvm fallback). A pure-`android` selection that strips `jvm` from those producers removes the variant the android modules were resolving against.**

The symptom is unhelpful: Gradle's raw attribute-resolution wall of text on a **`compileDependencyFiles`** configuration (`*CompileDependencyFiles`), listing producer variants and consumer attributes, with **no hint that the selection is the cause**. If you arrived here from that error, the fix is one line — keep `jvm` **co-selected** with `android`:

```properties
kmptargets.targets=android,jvm
```

(or any preset that includes both). The jvm variant the android modules fall back to then still registers on every producer, and the edges resolve again. For a large android repo this is usually what you actually want: `android,jvm`, not bare `android`.

!!! note "The plugin cannot detect this for you"
    Selection is resolved one module at a time, so this conflict is **invisible to the plugin by construction** — and it usually originates in **external published libraries** (the `jvm`-only producers), which the plugin cannot model at all. `kmpTargetsInfo` on a producer confirms it lost its `jvm` leaf under the current selection, but you reason about the closure across the graph yourself. A future [doctor mode](https://github.com/rsicarelli/kmp-targets/issues/82) may surface project-edge closure gaps; it can never see external-dependency variants, so co-selecting `jvm` remains the rule.

## Binary-compatibility validator + selection

BCV writes one ABI dump per target. A narrowed lane that runs `apiCheck`/`apiDump` sees only the registered subset — committing dumps from a narrowed lane deletes the other targets' dumps. Rule: **run BCV tasks only from a full-selection lane** (`kmptargets.targets=all` or your repo's full set), and gate `apiDump` in CI to that lane.

## Troubleshooting first

Symptom → cause → fix pairs for all of the above live in [Troubleshooting](../help/troubleshooting.md).
