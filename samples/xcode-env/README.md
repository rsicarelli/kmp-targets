# Sample: opt-in Xcode-environment selection (`kmptargets.xcodeEnv`)

A standalone library shipping an iOS/macOS framework (`XcodeEnvShared`) that lets **Xcode's own build
environment drive selection** — issue #109. The single committed line

```properties
# kmp-targets.properties
kmptargets.xcodeEnv=true
```

turns Xcode's `SDK_NAME` / `ARCHS` / `CONFIGURATION` env vars into a *declared* selection layer, so the
consumer's Xcode build phase that calls `embedAndSign…AppleFrameworkForXcode` needs **no
`-Pkmptargets.targets=…`**.

## How it resolves

Precedence (highest first): CLI `-P` → `ORG_GRADLE_PROJECT_` env → **Xcode environment** → this file →
`gradle.properties` → `local.properties`.

| Invocation | Active selection | Why |
|---|---|---|
| `./gradlew build` (no Xcode env) | `iosArm64, iosSimulatorArm64` | committed fallback in `kmp-targets.properties` |
| Xcode build, `SDK_NAME=iphonesimulator ARCHS=arm64` | `iosSimulatorArm64` | Xcode env overrides the file with the one leaf Xcode is building |
| anything with `-Pkmptargets.targets=iosArm64` | `iosArm64` | explicit `-P` still wins over the Xcode env |

`CONFIGURATION=Debug|Release` narrows the framework's build types through the same `#108` intersection;
a custom configuration honors `KOTLIN_FRAMEWORK_BUILD_TYPE`. An unknown SDK falls through to the
committed fallback (it never fails the build).

## Try it

```bash
task publish-local

# Committed fallback (no Xcode env):
./gradlew -p samples/xcode-env kmpTargetsInfo

# Simulate Xcode (macOS host) — narrows to one leaf, then links the framework:
SDK_NAME=iphonesimulator ARCHS=arm64 CONFIGURATION=Debug \
  ./gradlew -p samples/xcode-env verifyXcodeEnvNarrowed linkDebugFrameworkIosSimulatorArm64
```

The `verifyXcodeEnvNarrowed` task asserts the narrowing (the macOS CI lane runs exactly this); `inspect`
the resolved origin any time with `kmpTargetsInfo`, which prints `source: Xcode environment
(SDK_NAME/ARCHS)`.
