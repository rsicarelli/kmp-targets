# Xcode Environment

When Xcode invokes Gradle for `embedAndSign…AppleFrameworkForXcode`, it exports `SDK_NAME`, `ARCHS`, and `CONFIGURATION` — KGP already reads them to drive the embed task. Yet the consumer's Xcode build phase still has to pass `-Pkmptargets.targets=…` so the right leaf registers. The env vars already carry the answer.

The opt-in **`kmptargets.xcodeEnv`** flag closes that gap: with it on, Xcode's environment becomes a *declared* selection layer, so the Xcode build phase needs **no `-P`**.

```properties
# kmp-targets.properties — committed, team-shared
kmptargets.xcodeEnv=true
```

This is explicit by design. The committed flag is the act you review; the env is then a declared input, not ambient magic. With the flag off, the Xcode variables are **never read** (no configuration-cache inputs added).

## What it maps

`SDK_NAME` (matched by prefix; it carries a version suffix like `iphonesimulator18.0`) plus a single `ARCHS` value resolve to one leaf:

| `SDK_NAME` | `arm64` | `x86_64` | other arch |
|---|---|---|---|
| `iphoneos` | `iosArm64` | — | — |
| `iphonesimulator` | `iosSimulatorArm64` | `iosX64` | — |
| `appletvos` | `tvosArm64` | — | — |
| `appletvsimulator` | `tvosSimulatorArm64` | `tvosX64` | — |
| `watchos` | `watchosDeviceArm64` | — | `arm64_32` → `watchosArm64`, `armv7k` → `watchosArm32` |
| `watchsimulator` | `watchosSimulatorArm64` | `watchosX64` | — |
| `macosx` | `macosArm64` | `macosX64` | — |

`CONFIGURATION` feeds [`kmptargets.framework.buildTypes`](apple-framework.md#build-types-per-lane): `Debug`/`Release` map case-insensitively. A **custom** configuration (`Staging`, `QA`, …) is not a build type, so — mirroring KGP — the `KOTLIN_FRAMEWORK_BUILD_TYPE` env var is consulted as the fallback.

An **unknown** SDK, an unrecognized or **multi-architecture** `ARCHS` (a fat build like `arm64 x86_64`), or a custom configuration with no fallback all resolve to nothing and **fall through to the next layer** — they never fail the build. `IS_MACCATALYST` is not consulted.

## Precedence

The Xcode-environment source slots **below** the per-invocation overrides and **above** the dedicated files, so a CI lane or a manual run can still override it, while inside an Xcode build the active SDK/arch wins over any committed default:

1. `-Pkmptargets.targets=…` / `-Pkmptargets.framework.buildTypes=…` (CLI)
2. `ORG_GRADLE_PROJECT_…` environment
3. **Xcode environment** (`SDK_NAME`/`ARCHS` → selection, `CONFIGURATION` → build types) — when `kmptargets.xcodeEnv=true`
4. `kmp-targets.local.properties` → `kmp-targets.properties` → `gradle.properties` → `local.properties`

[`kmpTargetsInfo`](diagnostics.md#kmptargetsinfo) names the winning origin — `Xcode environment (SDK_NAME/ARCHS)` for the selection, `Xcode environment (CONFIGURATION)` for the build types — so a build driven by Xcode is never opaque.

## The payoff

The consumer's Xcode "Run Script" build phase drops the `-P` entirely:

```bash
# Before — the run-script had to re-derive and pass the selection:
./gradlew :shared:embedAndSignAppleFrameworkForXcode -Pkmptargets.targets=iosSimulatorArm64

# After (kmptargets.xcodeEnv=true) — Xcode's own SDK_NAME/ARCHS/CONFIGURATION drive it:
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

`embedAndSign…` stays KGP's task and contract — the plugin only maps the env Xcode already sets onto the selection. Outside Xcode (no env), selection falls through to the committed `kmp-targets.properties`, so a plain `./gradlew` build is unchanged and fully reproducible. See the runnable [`samples/xcode-env`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/xcode-env) build.

## Out of scope

Owning `embedAndSign` (KGP's task), generating the Xcode run-script phase, multi-architecture fat-simulator handling, and `IS_MACCATALYST` are deliberately not covered.
