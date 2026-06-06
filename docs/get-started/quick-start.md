# Quick Start

Five minutes from apply to a narrowed build. Each step builds on the previous one.

## 1. Apply and declare what the module *can* build

```kotlin
// feature-mobile/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

kmpTargets {
    supports { mobile }   // androidTarget + the iOS leaves
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ...
        }
    }
}
```

`supports { }` is the module's *supported set* — the complete list of targets this module is ever allowed to register. The vocabulary is type-safe set algebra: presets (`all`, `mobile`, `apple`, `web`, `jvmFamily`, …) and leaves (`jvm`, `iosArm64`, `linuxX64`, …) compose with `+` and `-`.

!!! warning "Ordering: apply everything first, then `supports`"
    `supports` registers targets **eagerly**, the moment it runs. Apply all plugins that influence registration **before** the `kmpTargets { }` block — most importantly the Android Gradle plugin when the module supports `androidTarget`. Calling `supports` more than once unions; a registered target can't be retracted.

## 2. Build everything (default)

With no selection configured, every supported target registers:

```bash
./gradlew :feature-mobile:build
```

## 3. Narrow the build to what you need *now*

```bash
./gradlew :feature-mobile:build -Pkmptargets.targets=iosArm64
```

Only `iosArm64` registers — `selection ∩ supported`. The Android/JVM/simulator tasks don't run slow; they *don't exist*.

For a durable choice, use the config files instead of the flag:

```properties
# kmp-targets.properties — committed team default
kmptargets.targets=jvm,iosSimulatorArm64
```

```properties
# kmp-targets.local.properties — git-ignored personal override (beats the committed default)
kmptargets.targets=iosArm64
```

## 4. Inspect what the plugin decided

```bash
./gradlew :feature-mobile:kmpTargetsInfo -q
```

```console
kmp-targets — :feature-mobile

Selection (what to build now)
  targets:  iosArm64
  source:   command line (-Pkmptargets.targets)

Supported (what this module can build)
  declared: yes
  targets:  androidTarget, iosArm64, iosSimulatorArm64, iosX64

Registered (selection ∩ supported)
  targets:  iosArm64
```

The `source` line names the winning [selection layer](../user-guide/selection-layers.md), so a surprising selection is a one-command diagnosis.

## Where to next

- **[Selection DSL](../user-guide/selection-dsl.md)** — the full `supports { }` story: eagerness, unions, escape hatches
- **[Selection Layers](../user-guide/selection-layers.md)** — the precedence chain in detail
- **[Targets Reference](../user-guide/targets-reference.md)** — every preset and leaf
- **[CI Matrix](../user-guide/ci-matrix.md)** — per-host selections on a 3-OS matrix
