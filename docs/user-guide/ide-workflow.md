# IDE Workflow

Big KMP build, slow Sync? You only work one platform at a time, so let the IDE index only that one.

This is the everyday loop: pick the platform you're touching, Sync, get fast indexing and correct IntelliSense for it. Switch platforms by editing one line and re-syncing — **no `gradlew stop`, no daemon kill.**

## The recipe

**1. Pin your platform** in `kmp-targets.local.properties` (git-ignored, per-developer — it's *your* machine's preference, not the team's):

```properties
# kmp-targets.local.properties
kmptargets.targets=iosArm64
```

**2. Sync** (the Gradle elephant / "Reload All Gradle Projects"). Only `iosArm64` registers, so only its source sets index and only its tasks exist.

**3. Switch platforms** when you move to another. Edit the same line:

```properties
kmptargets.targets=jvm
```

…and Sync again. That's the whole loop.

!!! tip "Working on Android?"
    Use `kmptargets.targets=android,jvm` — co-selecting `jvm` keeps Android consumers resolving against the jvm-fallback variant. See [the asymmetric case](recipes.md#the-asymmetric-case-android-and-the-jvm-fallback).

## Why no daemon kill

`kmp-targets.local.properties` (and `kmp-targets.properties`) are **tracked configuration-cache inputs**. Editing one invalidates the cache, so the next Sync re-configures with the new target set — automatically. Leaving them untouched keeps the cache hit.

If you've used homegrown target-switching before and got used to running `gradlew stop` after every flip, you don't need it here. That ritual is the symptom of an *untracked* property read; this plugin tracks the file, so the edit alone is enough.

!!! note "What you can't avoid"
    Switching the registered target set is a real re-configuration + re-import — inherently slower than a no-op Sync. The win is the **result**: a smaller model (fewer targets) indexes faster. You skip the *extra* daemon-restart tax, not the Sync itself. And because [each value is its own cache entry](../why-kmp-targets.md#the-configuration-cache-trade-off), flipping *back* to a platform you've already used is a cache hit.

## Confirm what indexed

Not sure which targets the IDE actually got? Ask the plugin:

```bash
./gradlew :app:kmpTargetsInfo
```

It prints the resolved selection, the winning source (so a stale `kmp-targets.local.properties` is obvious), and the registered intersection. Full tour: [Diagnostics](diagnostics.md).

## Terminal & CI use the same key

The IDE preference and a one-off terminal run share one vocabulary — `kmptargets.targets`:

```bash
# one build, no file edit, beats the file:
./gradlew :app:assemble -Pkmptargets.targets=iosArm64
```

A Makefile/Taskfile target, a CI matrix job, and your IDE all speak the same string. Precedence (CLI beats env beats your local file beats the committed file): [Selection Layers](selection-layers.md).
