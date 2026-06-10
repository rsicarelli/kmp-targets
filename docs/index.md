# kmp-targets

**Dynamically select which Kotlin Multiplatform targets to build.**

[![Build](https://img.shields.io/github/actions/workflow/status/rsicarelli/kmp-targets/ci.yml)](https://github.com/rsicarelli/kmp-targets/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.rsicarelli/kmp-targets-gradle-plugin)](https://central.sonatype.com/artifact/com.rsicarelli/kmp-targets-gradle-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/KGP-2.2%2B-blue)](https://kotlinlang.org)

One Gradle property decides what your build registers. Targets you don't select are never registered with KGP, so their compile/link/KSP/publish tasks never exist — cutting sync and build times whenever you only need a subset.

```properties
# kmp-targets.properties (committed), kmp-targets.local.properties (personal), or -P
kmptargets.targets=jvm,iosArm64
```

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

kmpTargets {
    supports { mobile + web - iosX64 }   // presets and leaves compose as set algebra
}
```

The plugin registers `selection ∩ supported` per module: the module declares what it *can* build, the global selection decides what you *want* right now.

**Status:** alpha (pre-1.0). Roadmap: user-defined hierarchy groups, XCFramework helpers.

**[Why explicit selection? →](why-kmp-targets.md)**

---

## ✨ What you get

- ✅ **Explicit, type-safe target declaration** — the whole vocabulary (`all`, `mobile`, `apple`, `jvm`, `iosArm64`, …) in scope as set algebra; no strings, no imports
- ✅ **Three-layer selection** — committed team default → git-ignored personal override → per-invocation flag, all through the single `kmptargets.targets` key
- ✅ **Minimal hierarchy template** — only the intermediate source sets your registered targets need
- ✅ **`kmpTargetsInfo`** — a read-only report of the resolved selection, its winning source, and the registered intersection
- ✅ **Advisories, promotable to errors** — host-compatibility, deprecated targets, Android-without-AGP, inert modules; strict mode turns them into failures in CI
- ✅ **Build-logic friendly** — `registered()` / `onRegistered` expose the registered set without touching KGP internals; configuration-cache and Isolated Projects compatible

---

## 🚀 Start here

1. **[Why kmp-targets?](why-kmp-targets.md)** — the problem and the thesis, in 2 minutes
2. **[Installation](get-started/index.md)** — coordinates, repositories, prerequisites
3. **[Quick Start](get-started/quick-start.md)** — apply, declare, select, inspect, in 5 minutes
4. **[Selection DSL](user-guide/selection-dsl.md)** — `supports { }`, eagerness, set algebra
5. **[CI Matrix](user-guide/ci-matrix.md)** — one selection vocabulary across every CI host

---

## Requirements

| Consumer surface | Floor |
|---|---|
| Gradle (`kotlin-dsl` build-logic) | **8.11+** |
| Daemon JVM | **JDK 17** |
| Kotlin Gradle Plugin | **2.2+** |

**[Full compatibility story →](compatibility.md)**

---

## License

kmp-targets is licensed under the [Apache License 2.0](https://github.com/rsicarelli/kmp-targets/blob/main/LICENSE).
