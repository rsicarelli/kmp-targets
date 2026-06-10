# Recipes

Build-logic patterns from real multi-module adoptions. Each is a few lines in a convention plugin, keyed off [`registered()` / `onRegistered`](build-logic.md) rather than KGP internals.

## Gate compilation on inert modules

A narrowed lane can leave a module with zero registered targets, and KGP's commonMain metadata compilation [fails on a platform-less module](advisories.md#inert-modules). Gate it right after `supports { }`:

```kotlin
if (kmpTargets.registered().isEmpty()) {
    tasks.withType(KotlinCompilationTask::class.java).configureEach { enabled = false }
}
```

When only a native-wired codegen processor is at risk, gate on the narrower `kmpTargets.registered(KmpTargetSet.native).isEmpty()` — next recipe.

## commonMain KSP needs a native target

A processor that generates into `commonMain` runs on the commonMain metadata compilation (`kspCommonMainKotlinMetadata`). KSP only creates that task when a native target registers, so a native-less lane (`jvm`, `android,jvm`) skips generation — the failure ranges from unresolved references to a green build missing codegen. There is no per-platform workaround: K2 forbids `commonMain` from referencing platform-generated symbols. Gate right after `supports { }`:

```kotlin
if (kmpTargets.registered(KmpTargetSet.native).isEmpty()) {
    logger.warn(
        "ksp: ':${project.name}' generates commonMain code but no native target is " +
        "registered — symbol processing is skipped for this selection."
    )
    tasks.withType(KotlinCompilationTask::class.java).configureEach { enabled = false }
}
```

The gate is `native`, not the metadata compilation itself: `compileCommonMainKotlinMetadata` tracks a shared commonMain (≥2 platform targets), while KSP's commonMain route needs native presence specifically. [`kmpTargetsInfo`](diagnostics.md#kmptargetsinfo) shows what registered per lane.

## Per-target configuration wiring

The canonical `onRegistered` use — per-target configurations with the Gradle name pre-mangled, rename-proof because [`configurationName`](build-logic.md#configuration-name-helpers) follows [`targetName`](selection-dsl.md#renaming-the-jvm-target):

```kotlin
kmpTargets.onRegistered { add(it.configurationName("ksp"), processorDep) }
```

!!! warning "Never snapshot `kotlin.targets` eagerly"
    `kotlin.targets.forEach { … }` in a convention plugin sees only what registered before that line ran. `onRegistered` replays past registrations and fires for future ones.

## Selection-gated eager AGP application

`androidTarget` needs an Android Gradle plugin applied before `supports { }` ([the ordering rule](advisories.md#android-target-without-agp)). Applying AGP to every module wastes configuration time and creates Android tasks/configurations that are useless under android-less lanes. A **library** convention applies it only when Android is selected:

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

The gate is `KmpTarget.Jvm.Android in resolvedSelection()`: the same value registration intersects against, so gate and registration never disagree.

!!! warning "Apply AGP before `supports { }`, never after"
    `supports { }` checks for AGP at that instant and [skips `androidTarget`](advisories.md#android-target-without-agp) if absent. AGP applied after — or in `afterEvaluate` — misses registration with no error outside [strict mode](advisories.md#strict-mode).

!!! warning "Gate downstream AGP reads with the same predicate"
    Any read of AGP-owned configuration (`android { }`, `compileSdk`, a `LibraryExtension` lookup) crashes under a non-Android selection if it runs unconditionally. Keep it inside the same `if (androidSelected)`.

!!! note "`com.android.application` modules are exempt"
    An app module gets AGP from its own plugin block. Do not apply `com.android.library` to it and do not gate its AGP behind the selection — this recipe is for library modules only.

A companion plugin that must run only when AGP is present should react to AGP instead of re-testing the selection:

```kotlin
pluginManager.withPlugin("com.android.library") {
    pluginManager.apply("org.gradle.android.cache-fix")
}
```

Confirm per lane with [`kmpTargetsInfo`](diagnostics.md#kmptargetsinfo). There is no plugin hook for this — the gate is one `if` over `resolvedSelection()`, and the application-vs-library exemption is a build-logic decision ([#76](https://github.com/rsicarelli/kmp-targets/issues/76)).

## Lane-agnostic CI invocations

Aggregate tasks (`build`, `check`) only cover registered targets, so the same invocation works in every lane:

```bash
./gradlew check "-Pkmptargets.targets=$LANE_TARGETS"
```

Avoid hardcoding per-target task names (`:m:iosArm64Test`): they break under renames and lane changes. For explicit compile/test umbrellas, opt into [`kmpCompileAll` / `kmpTestAll`](ci-matrix.md#umbrella-tasks).

## Selection vs the dependency graph

Selection is intersected per module. If `:app` supports `jvm + iosArm64` and depends on `:lib` that only supports `jvm`, an `iosArm64` build of `:app` fails at variant resolution — `:lib` has no iOS variant. A module's supported set must cover every target its dependents build. Check both sides with [`kmpTargetsInfo`](diagnostics.md#kmptargetsinfo) and widen `:lib`'s `supports { }` (or narrow `:app`'s).

### The asymmetric case: android and the jvm fallback

- **Symptom**: Gradle's attribute-resolution error on a `*CompileDependencyFiles` configuration, listing producer variants and consumer attributes, with no mention of the selection.
- **Cause**: an android consumer resolves against a producer's `jvm` variant when the producer ships no `androidTarget` (the jvm fallback). A pure-`android` selection (`kmptargets.targets=android`) stops those producers from registering `jvm`, removing the variant the android modules were resolving against.
- **Fix**: co-select `jvm` with `android`:

```properties
kmptargets.targets=android,jvm
```

For a large android repo this is usually the right lane: `android,jvm`, not bare `android`.

The conflict usually originates in external `jvm`-only libraries, which the plugin cannot model. [Doctor](diagnostics.md#project-edge-closure) surfaces the `project(...)`-edge form of the gap; for external dependencies, co-selecting `jvm` is the rule.

## Binary-compatibility validator + selection

BCV writes one ABI dump per target. A narrowed lane running `apiCheck`/`apiDump` sees only the registered subset — committing dumps from a narrowed lane deletes the other targets' dumps. Run BCV tasks only from a full-selection lane (`kmptargets.targets=all` or your repo's full set), and gate `apiDump` in CI to that lane.

## Troubleshooting first

Symptom → cause → fix pairs for all of the above: [Troubleshooting](../help/troubleshooting.md).
