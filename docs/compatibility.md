# Compatibility

## Floors

The published jar targets the oldest supported consumer, not the toolchain the repo builds with:

| Consumer surface | Floor |
|---|---|
| `kotlin-dsl` build-logic (Gradle-embedded Kotlin) | Gradle **8.11+** / embedded Kotlin **2.0** |
| Daemon JVM | **JDK 17** |
| Kotlin Gradle Plugin at runtime | **KGP 2.2+** (developed and tested against 2.3.21) |

## How the floors are enforced

The build pins the emitted output: Kotlin `languageVersion`/`apiVersion` 2.0, `jvmTarget` 17 with `--release` protection, and a `kotlin-stdlib` 2.0.21 POM dependency. A `verifyCompatFloors` task inspects the built jar on every `check`, so the floors cannot silently regress. Why this approach: [Design](why-kmp-targets.md#compatibility-floors).

## Version pairing

| kmp-targets | Kotlin/KGP | Gradle |
|---|---|---|
| 0.1.x | 2.2+ (built against 2.3.21) | 8.11+ (repo runs 9.5.1, configuration cache on) |
