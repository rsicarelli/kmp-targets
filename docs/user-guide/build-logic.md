# Build Logic

The plugin ships one extension (`KmpTargetsExtension`) and one set of public types (`KmpTargetSet`, `KmpTarget`) — no bundled "preset" plugin ids. Teams that want `apply by id, no body` ergonomics build their own conventions in `build-logic/`:

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

The DSL is the primitive; convention plugins are an optional layer you own.

The build-logic build depends on the artifact directly:

```kotlin
// build-logic/build.gradle.kts
dependencies {
    implementation("com.rsicarelli:kmp-targets-gradle-plugin:0.1.0")
}
```

## Querying what registered

Conventions wire per-target things after registration — the canonical case is KSP, where each target gets its own configuration (`kspIosArm64`, `kspIosSimulatorArm64`, …). The extension exposes the registered set directly; build-logic never imports KGP konan types or hand-rolls name mangling:

```kotlin
// Per-leaf hook with the actual Gradle name, pre-mangled:
kmpTargets.onRegistered { add("ksp${it.gradleNameCapitalized}", dep) }

// Snapshots, family-sliced with plain set algebra:
kmpTargets.registered()                          // everything registered so far, as KmpTargetSet
kmpTargets.registered(KmpTargetSet.appleMobile)  // just the registered iOS leaves
```

## Configuration-name helpers

`"ksp${it.gradleNameCapitalized}"` is still a string to get wrong, and the test configuration is where it breaks: a hardcoded `kspJvmTest` throws `Configuration 'kspJvmTest' not found` once a [`targetName(jvm, "desktop")`](selection-dsl.md#renaming-the-jvm-target) rename turns it into `kspDesktopTest`. `RegisteredTarget` owns that grammar:

```kotlin
kmpTargets.onRegistered {
    add(it.configurationName("ksp"), processor)      // kspJvm / kspIosArm64 / kspDesktop (renamed)
    add(it.testConfigurationName("ksp"), processor)  // kspJvmTest / … / kspDesktopTest (renamed)
}
```

The prefix is tool-generic — any per-target configuration naming follows the same shape.

## `onRegistered` — the ordering-immune hook

`onRegistered` has `configureEach` semantics:

- Hooked before the module's `supports { }`, it fires as each leaf registers.
- Hooked after, it replays for the leaves already registered.
- A later `supports { }` union fires only its delta.

That makes it the right choice for convention plugins, which can't control where the consuming module places its blocks. The `registered()` snapshot is the simple read for build-script code that runs after `supports { }` — a point-in-time value; a later `supports { }` call can union in more leaves.

## `RegisteredTarget`

Each `onRegistered` callback receives a `RegisteredTarget`: the model leaf plus the Gradle target name registration actually used. A jvm leaf renamed via [`targetName(jvm, "desktop")`](selection-dsl.md#renaming-the-jvm-target) reports `gradleName == "desktop"`, so configuration wiring keeps working without the convention knowing about the rename.

`registered()` mirrors real registrations, not the abstract `selection ∩ supported` plan:

- without KGP applied it is empty (and `onRegistered` never fires)
- an `androidTarget` skipped by the [AGP guard](advisories.md#android-target-without-agp) stays absent

KSP wiring can never target a configuration that doesn't exist.

!!! warning "Configuration-time tool"
    Like `supports`, the callback (and the extension itself) is a configuration-time API — never hold either from a task action; that breaks the configuration cache.

## The canonical sample

The [`eager-conventions` sample](../samples/index.md) applies a `kmptargets.module` convention plugin from the sample's own `build-logic/`, using `onRegistered` as the canonical pattern — plus a regression gate (`verifyEagerTargets`) asserting exactly the expected leaves register.

## Related

- [Recipes](recipes.md) — KSP gates, inert-module gating, AGP ordering
- [Advisories](advisories.md#inert-modules) — the inert-module trap and its `registered().isEmpty()` gate
