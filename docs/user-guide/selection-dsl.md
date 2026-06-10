# Selection DSL

## `supports { }` — the module's supported set

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

kmpTargets {
    supports { mobile + web - iosX64 }   // presets and leaves compose freely
}
```

Every preset (`all`, `mobile`, `apple`, `web`, `jvmFamily`, …) and every leaf (`jvm`, `iosArm64`, `linuxX64`, …) is in scope inside the block; `+` unions, `-` subtracts. No imports, no strings. Full vocabulary: [Targets Reference](targets-reference.md).

Build-logic and callers that need raw values use the overload:

```kotlin
supports(KmpTargetSet.mobile + KmpTargetSet.web)
```

## Explicit by default

A module that never calls `supports` registers nothing — like plain KGP, where every target is declared by hand. To build whatever the developer currently selects:

```kotlin
kmpTargets { supports { all } }   // global selection alone decides
```

The selection is global (the `kmptargets.targets` property — see [Selection Layers](selection-layers.md)); there is no per-module selection block. The plugin registers `selection ∩ supported` per module.

A project-wide default for when `kmptargets.targets` is unset can be set from build-logic via the `defaultSelection` property; any actual `kmptargets.targets` value overrides it.

## Eagerness and ordering

`supports` registers eagerly — the moment it runs. Two rules follow:

1. **Apply everything first.** Plugins that influence registration (most importantly AGP for `androidTarget` — see [Advisories](advisories.md#android-target-without-agp)) must be applied before `supports { }` runs.
2. **Call it before anything reads `kotlin.targets`.** Code that snapshots the target container earlier sees a stale view. Build-logic should prefer [`onRegistered`](build-logic.md#onregistered-the-ordering-immune-hook), which is ordering-immune.

Calling `supports` more than once unions — a registered target can't be retracted. A later call registers only its delta and re-checks the AGP guard.

## Renaming the JVM target

Projects with a legacy `kotlin.jvm("desktop")` target can keep that name — source dirs (`src/desktopMain`), artifact suffixes, and task names (`compileKotlinDesktop`) included:

```kotlin
kmpTargets {
    targetName(jvm, "desktop") // must come BEFORE supports { } — registration is eager and one-way
    supports { mobile + jvm }
}
```

- **Selection token unchanged.** Builds still select with `jvm` (or the `desktop` alias). Only the registered Gradle target, its source sets, and the artifact suffix follow the custom name.
- **jvm leaf only.** `androidTarget`'s name is fixed by AGP; native/web names are derived by KGP. `targetName` fails loud on any other leaf, on a blank name, and when called after `supports { }` already registered the jvm leaf.
- **Hierarchy unaffected.** KGP's matchers key off the platform type, so the [minimal template](hierarchy-template.md) attaches a renamed target like a plain `jvm`.
- **Introspection.** [`kmpTargetsInfo`](diagnostics.md#kmptargetsinfo) prints `jvm (registered as: desktop)`; build-logic reads [`RegisteredTarget.gradleName`](build-logic.md#registeredtarget). A hardcoded `compileKotlinJvm` in CI matches zero tasks under the rename — the [umbrella tasks](ci-matrix.md#umbrella-tasks) wire the real `compileKotlinDesktop`.

The [`desktop-named` sample](../samples/index.md) runs `jvm + iosArm64` with the jvm leaf renamed to `desktop`.

## Escape hatch: mixing with raw KGP

`supports { }` and explicit `kotlin { }` target calls coexist — the plugin registers through plain KGP APIs. The [`escape-hatch-dsl` sample](../samples/index.md) keeps a raw `kotlin.jvm { }` block next to `supports { jvm + linuxX64 }` for target-level configuration the DSL doesn't model.

## Related

- [Selection Layers](selection-layers.md) — where the global selection comes from
- [Build Logic](build-logic.md) — wrapping the DSL in your own convention plugins
