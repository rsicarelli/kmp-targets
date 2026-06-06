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

Every preset (`all`, `mobile`, `apple`, `web`, `jvmFamily`, …) and every leaf (`jvm`, `iosArm64`, `linuxX64`, …) is in scope inside the block as **set algebra** — `+` unions, `-` subtracts. No imports, no strings. The full vocabulary lives in the [Targets Reference](targets-reference.md).

Build-logic and callers that need raw values use the overload:

```kotlin
supports(KmpTargetSet.mobile + KmpTargetSet.web)
```

## Explicit by default

A module that never calls `supports` registers **nothing** — exactly like plain KGP, where every target is declared by hand. To build "whatever the developer currently selects", opt in explicitly:

```kotlin
kmpTargets { supports { all } }   // global selection alone decides
```

The selection itself is **global** (the `kmptargets.targets` property — see [Selection Layers](selection-layers.md)); there is no per-module selection block. The plugin registers `selection ∩ supported` per module.

A project-wide default for when `kmptargets.targets` is unset can be set from build-logic via the `defaultSelection` property; any actual `kmptargets.targets` value overrides it.

## Eagerness and ordering

`supports` registers **eagerly** — the moment it runs. Two rules follow:

1. **Apply everything first.** Plugins that influence registration (most importantly the Android Gradle plugin for `androidTarget` — see [Advisories](advisories.md#android-target-without-agp)) must be applied *before* `supports { }` runs.
2. **Call it before anything reads `kotlin.targets`.** Code that snapshots the target container before `supports` runs sees a stale view. Build-logic should prefer [`onRegistered`](build-logic.md#onregistered-the-ordering-immune-hook), which is ordering-immune.

Calling `supports` more than once **unions** — an already-registered target can't be retracted. A later call registers only its delta (and re-checks the AGP guard, so AGP applied between two calls lets the later pass register `androidTarget` normally).

## Escape hatch: mixing with raw KGP

`supports { }` and explicit `kotlin { }` target calls coexist — the plugin registers through plain KGP APIs. The [`escape-hatch-dsl` sample](../samples/index.md) keeps a raw `kotlin.jvm { }` block next to `supports { jvm + linuxX64 }` for target-level configuration the DSL doesn't model.

## Related

- [Selection Layers](selection-layers.md) — where the global selection comes from
- [Build-Logic Conventions](build-logic.md) — wrapping the DSL in your own convention plugins
- [Renaming the JVM Target](jvm-rename.md) — `targetName(jvm, "desktop")`
