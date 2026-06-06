# Compatibility

## Floors

The published jar targets the **oldest supported consumer**, not the toolchain the repo builds with:

| Consumer surface | Floor |
|---|---|
| `kotlin-dsl` build-logic (Gradle-embedded Kotlin) | Gradle **8.11+** / embedded Kotlin **2.0** |
| Daemon JVM | **JDK 17** |
| Kotlin Gradle Plugin at runtime | **KGP 2.2+** (developed and tested against 2.3.21) |

## How the floors are enforced

The repo itself builds with the latest mise-pinned JDK and Kotlin — there are **no Gradle toolchains** (see [why toolchains are rarely a good idea](https://jakewharton.com/gradle-toolchains-are-rarely-a-good-idea/)). Instead the build pins the **emitted output**:

- Kotlin `languageVersion`/`apiVersion` **2.0** — metadata the Gradle 8.x embedded compiler can read
- `jvmTarget` 17 + `-Xjdk-release=17` / `--release 17` — bytecode JDK 17 daemons can load, with accidental-new-API protection
- a `kotlin-stdlib` **2.0.21** POM dependency — so floor consumers never pull 2.3-metadata stdlib jars onto their `kotlin-dsl` compile classpath

A `verifyCompatFloors` task inspects the **built jar** (bytecode versions, metadata versions, resolved stdlib) on every `check` — the floors are verified artifacts, not promises, and cannot silently regress.

## Philosophy

- **Track KGP's target model, deliberately.** The leaf vocabulary covers every target KGP 2.3.21 ships (minus the removed `linuxArm32Hfp`); the [deprecated set](user-guide/advisories.md#deprecated-targets) is pinned against Kotlin's [target-support page](https://kotlinlang.org/docs/native-target-support.html) and updated intentionally with Kotlin upgrades — never inferred at runtime.
- **Honor explicit selection everywhere.** No host-specific filtering, no version-specific surprises: the same selection registers the same set on every machine that meets the floors.
- **The samples are the compat suite.** A 3-OS CI matrix builds the multi-module sample per host on every change, and an Isolated-Projects sample gates that mode — claims in this table are exercised, not asserted.

## Version pairing

| kmp-targets | Kotlin/KGP | Gradle |
|---|---|---|
| 0.1.x | 2.2+ (built against 2.3.21) | 8.11+ (repo runs 9.5.1, configuration cache on) |

!!! note
    Pre-1.0, minor versions may contain breaking changes — they are called out in the [CHANGELOG](https://github.com/rsicarelli/kmp-targets/blob/main/CHANGELOG.md) (e.g. the `KMP_TARGETS` → `kmptargets.targets` key rename).
