# Targets Reference

Every target Kotlin Multiplatform (KGP 2.3.21) supports, except the deprecated `linuxArm32Hfp`. The same vocabulary works in the [`supports { }` DSL](selection-dsl.md), the [`kmptargets.targets` property](selection-layers.md), and [CI matrix values](ci-matrix.md).

## Leaves

| Family | Targets |
|---|---|
| JVM | `androidTarget` (alias `android`), `jvm` (alias `desktop`) |
| iOS | `iosArm64`, `iosSimulatorArm64`, `iosX64` |
| macOS | `macosArm64`, `macosX64` |
| watchOS | `watchosArm64`, `watchosArm32`, `watchosX64`, `watchosSimulatorArm64`, `watchosDeviceArm64` |
| tvOS | `tvosArm64`, `tvosX64`, `tvosSimulatorArm64` |
| Linux | `linuxX64`, `linuxArm64` |
| MinGW | `mingwX64` |
| Android Native | `androidNativeArm32`, `androidNativeArm64`, `androidNativeX86`, `androidNativeX64` |
| Web | `js`, `wasmJs`, `wasmWasi` |

Every leaf also accepts a kebab-case alias (e.g. `watchos-sim-arm64`, `linux-x64`, `android-native-arm64`), and parsing is case-insensitive.

## Presets

| Preset | Expands to |
|---|---|
| `all` | every shipped target |
| `native` | every Kotlin/Native target (Apple + Linux + MinGW + Android Native) |
| `apple` | all Apple platforms: iOS + macOS + watchOS + tvOS |
| `appleMobile` / `appleDesktop` | all iOS / all macOS |
| `appleWatch` / `appleTv` | all watchOS / all tvOS |
| `linux` | `linuxX64`, `linuxArm64` |
| `mingw` (alias `windows`) | `mingwX64` |
| `androidNative` | the four `androidNative*` targets |
| `web` | `js`, `wasmJs`, `wasmWasi` |
| `jvmFamily` | `androidTarget`, `jvm` |
| `mobile` | `androidTarget` + all iOS |

## Membership clarifications

Preset membership is worth spelling out where it's non-obvious:

- **`jvmFamily` *includes* Android** — it is `{androidTarget, jvm}`, the two JVM-bytecode leaves, not just `jvm`.
- **`mobile` = `androidTarget` + the iOS leaves** (`iosArm64`, `iosSimulatorArm64`, `iosX64`) — no watchOS, no macOS.
- **`androidNative` ≠ `androidTarget`** — the four `androidNative*` leaves are Kotlin/Native NDK targets; the Android *app* target is `androidTarget` (alias `android`).
- **Aliases**: `android` → `androidTarget`, `desktop` → `jvm`, `windows` → `mingw`. The alias and the canonical name are interchangeable in every layer.
- **Bare sub-family names are rejected**: `ios`, `macos`, `watchos`, `tvos` fail parsing with a hint pointing at the relevant leaf or `appleX` preset — they would be ambiguous between "all leaves" and "the device leaf".

## Deprecated leaves

Kotlin [marks](https://kotlinlang.org/docs/native-target-support.html) `macosX64`, `watchosX64`, and `tvosX64` **deprecated** (since Kotlin 2.3.20). They stay selectable and stay in every preset (`appleDesktop` still expands to both macOS leaves) — registering one logs a [deprecated-target advisory](advisories.md#deprecated-targets), and [`kmpTargetsInfo`](kmp-targets-info.md) annotates them `(deprecated)` in its vocabulary listing. (`iosX64` is low-tier but *not* deprecated.)

## Composing

Presets and leaves compose with `+` / `-` in the DSL and with `,` / `-` / `+` in the property grammar:

```kotlin
kmpTargets { supports { mobile + web - iosX64 } }
```

```properties
kmptargets.targets=appleMobile,-iosArm64,+jvm
```
