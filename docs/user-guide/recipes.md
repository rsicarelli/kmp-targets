# Recipes

Build-logic patterns collected from real multi-module adoptions. Each is a few lines in a convention plugin; all of them key off [`registered()` / `onRegistered`](build-logic.md) rather than KGP internals.

## Gate compilation on inert modules

A narrowed lane can leave a module with zero registered targets — and KGP's commonMain metadata compilation [fails on a platform-less module](advisories.md#inert-modules). Gate it right after `supports { }`:

```kotlin
if (kmpTargets.registered().isEmpty()) {
    tasks.withType(KotlinCompilationTask::class.java).configureEach { enabled = false }
}
```

This is the gate the inert-module advisory names. Variant: a metadata-only predicate for modules whose *native* fragment is the risky part — gate on `kmpTargets.registered(KmpTargetSet.native).isEmpty()` instead.

## commonMain KSP needs a native target

KSP processing of `commonMain` runs on a platform compilation. If your processor is wired to a native target, a selection without any native leaf silently skips generation — downstream code then fails with missing symbols. Gate and warn explicitly:

```kotlin
if (kmpTargets.registered(KmpTargetSet.native).isEmpty()) {
    logger.warn(
        "ksp: ':${project.name}' has no native target registered — " +
        "commonMain symbol processing is skipped for this selection."
    )
}
```

## Per-target configuration wiring

The canonical `onRegistered` use: per-target configurations (KSP, custom attributes), with the Gradle name pre-mangled — and rename-proof, because `gradleNameCapitalized` follows [`targetName`](jvm-rename.md):

```kotlin
kmpTargets.onRegistered { add("ksp${it.gradleNameCapitalized}", processorDep) }
```

!!! warning "Never snapshot `kotlin.targets` eagerly"
    `kotlin.targets.forEach { … }` in a convention plugin sees only what registered *before that line ran* — ordering-sensitive and silently incomplete. `onRegistered` replays past registrations and fires for future ones.

## Selection-gated eager AGP application

`androidTarget` needs AGP applied **before** `supports { }` ([the ordering rule](advisories.md#android-target-without-agp)). A convention can apply AGP only when Android is actually selected, avoiding AGP's configuration cost in non-Android lanes:

```kotlin
// in the convention plugin, before configuring kmpTargets
val selectsAndroid = /* read kmptargets.targets via providers and check for android/jvmFamily/mobile */
if (selectsAndroid) {
    pluginManager.apply("com.android.library")
}
// note: com.android.application modules apply AGP unconditionally — the app module IS Android
```

## Lane-agnostic CI invocations

Aggregate tasks (`build`, `check`) only cover registered targets, so the **same Gradle invocation works in every lane** — the selection does the narrowing, not the task list:

```bash
./gradlew check "-Pkmptargets.targets=$LANE_TARGETS"
```

Avoid hardcoding per-target task names (`:m:iosArm64Test`) in CI scripts: they break under renames and lane changes. If you must derive test tasks, derive them from `registered()` in a small task-listing helper, not from string conventions.

## Selection vs the dependency graph

Selection is **per-module**, intersected per-module. If `:app` supports `jvm + iosArm64` and depends on `:lib` that only supports `jvm`, an `iosArm64` build of `:app` fails at *variant resolution* — `:lib` has no iOS variant to offer. The error is KGP's wall of text, but the cause is set arithmetic: **a module's supported set must cover every target its dependents build**. Check both sides with `kmpTargetsInfo` and widen `:lib`'s `supports { }` (or narrow `:app`'s).

## Binary-compatibility validator + selection

BCV writes one ABI dump per target. A narrowed lane that runs `apiCheck`/`apiDump` sees only the registered subset — committing dumps from a narrowed lane deletes the other targets' dumps. Rule: **run BCV tasks only from a full-selection lane** (`kmptargets.targets=all` or your repo's full set), and gate `apiDump` in CI to that lane.

## Troubleshooting first

Symptom → cause → fix pairs for all of the above live in [Troubleshooting](../help/troubleshooting.md).
