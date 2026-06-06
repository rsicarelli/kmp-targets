# Build-Logic Conventions

The plugin ships just one extension (`KmpTargetsExtension`) and one set of public types (`KmpTargetSet`, `KmpTarget`) — there are **no bundled "preset" plugin ids**. Teams that want `apply by id, no body` ergonomics build their own conventions in `build-logic/`:

```kotlin
// build-logic/src/main/kotlin/my.mobile-module.gradle.kts
import com.rsicarelli.kmptargets.KmpTargetsExtension
import com.rsicarelli.kmptargets.model.KmpTargetSet

plugins {
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets")
}

extensions.configure<KmpTargetsExtension> {
    supports(KmpTargetSet.mobile)
}
```

```kotlin
// feature-mobile/build.gradle.kts
plugins { id("my.mobile-module") }
```

This is how a team centralizes per-module shape vocabulary in their own naming, instead of taking a vocabulary baked into this plugin's artifact. The DSL stays the primitive; convention plugins are an optional layer you own.

The build-logic build depends on the artifact directly:

```kotlin
// build-logic/build.gradle.kts
dependencies {
    implementation("com.rsicarelli:kmp-targets-gradle-plugin:0.1.0-SNAPSHOT")
}
```

## Querying what registered

Conventions routinely need to wire *per-target* things after registration — the canonical case is KSP, where each target gets its own configuration (`kspIosArm64`, `kspIosSimulatorArm64`, …). Without help, every convention re-derives family matching and name mangling from KGP internals:

```kotlin
// Before — KGP konan types plus hand-rolled name mangling:
kotlin.targets.withType<KotlinNativeTarget>()
    .matching { it.konanTarget.family == Family.IOS }
    .forEach { add("ksp${it.targetName.replaceFirstChar(Char::titlecase)}", dep) }
```

The extension exposes the **registered** set directly, so build-logic never touches `konanTarget`:

```kotlin
// After — per-leaf hook with the actual Gradle name, pre-mangled:
kmpTargets.onRegistered { add("ksp${it.gradleNameCapitalized}", dep) }

// Snapshots, family-sliced with plain set algebra:
kmpTargets.registered()                          // everything registered so far, as KmpTargetSet
kmpTargets.registered(KmpTargetSet.appleMobile)  // just the registered iOS leaves
```

## `onRegistered` — the ordering-immune hook

`onRegistered` has `configureEach` semantics:

- Hooked **before** the module's `supports { }`, it fires as each leaf registers.
- Hooked **after**, it replays for the leaves already registered.
- A later `supports { }` union fires only its delta.

That makes it the right choice for convention plugins, which can't control where the consuming module places its blocks. The `registered()` snapshot is the simple read for build-script code that runs after `supports { }` — but remember it's a point-in-time value; a later `supports { }` call can union in more leaves.

## `RegisteredTarget`

Each `onRegistered` callback receives a `RegisteredTarget`: the model leaf plus the Gradle target name registration **actually used**. A jvm leaf renamed via [`targetName(jvm, "desktop")`](jvm-rename.md) reports `gradleName == "desktop"`, so configuration wiring keeps working without the convention knowing about the rename.

`registered()` deliberately mirrors **real registrations**, not the abstract `selection ∩ supported` plan:

- without KGP applied it is empty (and `onRegistered` never fires)
- an `androidTarget` skipped by the [AGP guard](advisories.md#android-target-without-agp) stays absent

…so KSP wiring can never target a configuration that doesn't exist.

!!! warning "Configuration-time tool"
    Like `supports`, the callback (and the extension itself) is a configuration-time API — never hold either from a task action; that breaks the configuration cache.

## The canonical sample

The [`eager-conventions` sample](../samples/index.md) applies a `kmptargets.module` convention plugin from the sample's own `build-logic/`, using `onRegistered` as the canonical pattern — plus a regression gate (`verifyEagerTargets`) asserting exactly the expected leaves register.

## Related

- [Recipes](recipes.md) — concrete convention patterns: KSP gates, inert-module gating, AGP ordering
- [Advisories](advisories.md#inert-modules) — the inert-module trap and its `registered().isEmpty()` gate
