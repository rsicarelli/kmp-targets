# kmp-targets

**Dynamically select which Kotlin Multiplatform targets to build.**

[![Build](https://img.shields.io/github/actions/workflow/status/rsicarelli/kmp-targets/ci.yml)](https://github.com/rsicarelli/kmp-targets/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.rsicarelli/kmp-targets-gradle-plugin)](https://central.sonatype.com/artifact/com.rsicarelli/kmp-targets-gradle-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/KGP-2.2%2B-blue)](https://kotlinlang.org)

One Gradle property decides what your build registers. Targets you don't select are never registered with KGP, so their compile/link/KSP/publish tasks never exist.

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

**Status:** alpha. Roadmap: user-defined hierarchy groups, XCFramework helpers.

## Documentation

1. **[Quick Start](get-started/quick-start.md)** — apply, declare, select, inspect
2. **[Selection DSL](user-guide/selection-dsl.md)** — `supports { }`, set algebra, the JVM rename
3. **[CI](user-guide/ci-matrix.md)** — one selection vocabulary across every CI host
4. **[Design](why-kmp-targets.md)** — rationale and trade-offs

## Requirements

| Consumer surface | Floor |
|---|---|
| Gradle (`kotlin-dsl` build-logic) | **8.11+** |
| Daemon JVM | **JDK 17** |
| Kotlin Gradle Plugin | **2.2+** |

Full story: [Compatibility](compatibility.md).

## License

kmp-targets is licensed under the [Apache License 2.0](https://github.com/rsicarelli/kmp-targets/blob/main/LICENSE).
