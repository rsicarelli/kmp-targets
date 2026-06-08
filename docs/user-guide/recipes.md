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
