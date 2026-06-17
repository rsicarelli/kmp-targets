# Samples

Five standalone sample builds consume the plugin as an external project would: shared root version catalog, plugin resolved by version (mavenLocal in the dev loop, Maven Central for consumers).

## hello-world — the multi-module showcase

[`samples/hello-world`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world) is a heterogeneous multi-module project — each module demonstrates one shape:

| Module | Declares | Demonstrates |
|---|---|---|
| [`shared-core`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/shared-core) | `supports { all }` | the opt-in "build whatever is selected" module |
| [`feature-mobile`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/feature-mobile) | `supports { mobile }` | a mobile-only feature; selections outside `mobile` leave it empty (no AGP in the sample, so iOS leaves are its registrable shape) |
| [`jvm-tools`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/jvm-tools) | `supports { jvm }` | a JVM-only tooling module |
| [`core-and-apple`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/core-and-apple) | `supports { apple + jvm }` | preset + leaf composition |
| [`escape-hatch-dsl`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/escape-hatch-dsl) | `supports { jvm + linuxX64 }` | mixing the DSL with raw `kotlin { }` target configuration |
| [`desktop-named`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/desktop-named) | `targetName(jvm, "desktop")` + `supports { jvm + iosArm64 }` | the [JVM rename](../user-guide/selection-dsl.md#renaming-the-jvm-target): `src/desktopMain`, `compileKotlinDesktop` |
| [`pinned-intermediates`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/pinned-intermediates) | `supports { appleMobile }`, collapse off | [no-collapse](../user-guide/hierarchy-template.md#keeping-intermediates): `iosMain` survives a single-leaf selection |
| [`eager-conventions`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/eager-conventions) | applies `kmptargets.module` | the [convention-plugin pattern](../user-guide/build-logic.md) with an `onRegistered` regression gate (`verifyEagerTargets`) |
| [`legacy-default`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/legacy-default) | `supports { all }`, template off | opting out of the [minimal hierarchy](../user-guide/hierarchy-template.md) — KGP's default tree, side by side |
| [`plain-kmp`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/plain-kmp) | *no kmp-targets* | the non-interference proof: a module without the plugin is untouched |
| [`build-logic`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/build-logic) | — | the sample's own convention build: `kmptargets.module` plugin, [`KmpModule.kt`](https://github.com/rsicarelli/kmp-targets/blob/main/samples/hello-world/build-logic/src/main/kotlin/KmpModule.kt) is the canonical `onRegistered` consumer |

Drive it like CI does:

```bash
./gradlew -p samples/hello-world build "-Pkmptargets.targets=jvm,iosArm64"
./gradlew -p samples/hello-world :shared-core:kmpTargetsInfo -q
```

## isolated-projects — the compatibility gate

[`samples/isolated-projects`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/isolated-projects) is a two-module build ([`lib`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/isolated-projects/lib) supports `all`, [`app`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/isolated-projects/app) supports `jvm` and depends on `lib`) running with Gradle Isolated Projects enabled. `lib` carries a `verifyIsolatedConfiguration` regression gate.

## abi-bcv & abi-builtin — the ABI coverage gap

Two single-module libraries that support `jvm + linuxX64 + js` and commit ABI dumps under `api/`, one per tool: [`samples/abi-bcv`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/abi-bcv) uses [kotlinx binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator); [`samples/abi-builtin`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/abi-builtin) uses Kotlin's built-in `abiValidation`. Both show the [coverage gap](../user-guide/abi-validation.md): an ABI tool only sees the registered targets, so a narrowed lane under-covers — the built-in tool passes without validating the missing targets, BCV fails and suggests a destructive dump. See the per-sample READMEs.

```bash
./gradlew -p samples/abi-builtin checkKotlinAbi "-Pkmptargets.targets=jvm"  # green, but warns: js, linuxX64 not validated
./gradlew -p samples/abi-bcv     apiCheck                                   # full selection (recommended lane) → clean
```

## xcode-env — Xcode drives selection

[`samples/xcode-env`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/xcode-env) is a single-module library shipping an iOS/macOS framework with `kmptargets.xcodeEnv=true` committed. With no Xcode env it builds the committed fallback; when Xcode's `SDK_NAME`/`ARCHS`/`CONFIGURATION` are present they narrow selection to the one leaf being built — no `-P`. Its `verifyXcodeEnvNarrowed` task pins the narrowing; the [Xcode Environment](../user-guide/xcode-environment.md) guide explains the mapping.

```bash
./gradlew -p samples/xcode-env kmpTargetsInfo                                   # committed fallback
SDK_NAME=iphonesimulator ARCHS=arm64 CONFIGURATION=Debug \
  ./gradlew -p samples/xcode-env verifyXcodeEnvNarrowed                         # narrows to iosSimulatorArm64
```

## CI runs them

[`ci.yml`](https://github.com/rsicarelli/kmp-targets/blob/main/.github/workflows/ci.yml) builds the samples on every push; [`sample-matrix.yml`](https://github.com/rsicarelli/kmp-targets/blob/main/.github/workflows/sample-matrix.yml) builds hello-world per host with host-appropriate selections — the [CI matrix](../user-guide/ci-matrix.md) pattern — and on macOS links the `xcode-env` framework from the real Xcode environment.
