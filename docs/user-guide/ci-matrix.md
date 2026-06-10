# CI

Each runner builds only the targets it can host. The matrix passes a selection per job — the same `kmptargets.targets` strings used locally in `kmp-targets.local.properties` or `-P`. Why per-host selection pays (macOS pricing): [Design](../why-kmp-targets.md#the-problem).

## The matrix

```yaml
name: Build

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    strategy:
      fail-fast: false
      matrix:
        include:
          - os: ubuntu-latest
            targets: jvm,android,web,linuxX64,linuxArm64,androidNative
          - os: macos-latest
            targets: apple
          - os: windows-latest
            targets: windows
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '23'
      - uses: gradle/actions/setup-gradle@v6
      # Quoted on purpose: PowerShell (windows-latest) splits unquoted commas into an array.
      - run: ./gradlew build "-Pkmptargets.targets=${{ matrix.targets }}"
```

## Host → target mapping

| Runner | `kmptargets.targets` | Rationale |
|---|---|---|
| `ubuntu-latest` | `jvm,android,js,wasmJs,wasmWasi,linuxX64,linuxArm64,androidNative` | every non-Apple, non-MinGW target cross-compiles here (`android` requires AGP in your build; `web` is the `js,wasmJs,wasmWasi` preset) |
| `macos-latest` | `apple` (or `appleMobile` on PRs) | the only host that can compile/link Apple targets |
| `windows-latest` | `mingwX64` (alias `windows`) | the only host for MinGW |

## Notes

- **Same vocabulary.** Matrix values are plain [selection grammar](selection-layers.md#selection-grammar) strings — presets, leaves, and `-` exclusions all work. What CI builds is what [`kmpTargetsInfo`](diagnostics.md#kmptargetsinfo) prints locally for the same value.
- **Env form.** `ORG_GRADLE_PROJECT_kmptargets.targets` as a job `env:` is equivalent; `-P` is the canonical recommendation. Both sit above the committed `kmp-targets.properties` in [precedence](selection-layers.md).
- **Cost tier.** Restrict the macOS job to `appleMobile` on `pull_request` and run the full `apple` preset only on `push` to `main`.
- **Strict mode.** Set [`kmptargets.strict=true`](advisories.md#strict-mode) on jobs whose selection the host can fully compile — a misconfigured module fails the job instead of silently building nothing.
- **Configuration cache.** Each distinct selection is its own configuration-cache key; matrix jobs with different selections don't share an entry. See [Design](../why-kmp-targets.md#the-configuration-cache-trade-off).

## Umbrella tasks

`kmpCompileAll` and `kmpTestAll` are opt-in lifecycle tasks that depend on exactly the registered intersection — one stable task name per job, correct in every lane and under a [renamed jvm target](selection-dsl.md#renaming-the-jvm-target).

Hardcoded task lists fail two ways under a variable selection: a name absent from the lane (`compileReleaseKotlinAndroid` on an android-less selection) fails with `Task '…' not found`; under a renamed jvm target a literal `compileKotlinJvm` matches zero tasks and the job passes without compiling.

- **`kmpCompileAll`** — compiles every registered target's main compilation.
- **`kmpTestAll`** — runs every registered target's tests (targets without a test task, like device-only `iosArm64`, are skipped).

Opt in — the tasks add dependency edges to every registered compile/test task, and most builds only want them for CI:

```properties
# kmp-targets.properties
kmptargets.umbrellaTasks=true
```

The flag reads through the same [precedence chain](selection-layers.md) as every `kmptargets.` key.

Semantics:

- Registered in every module the plugin is applied to, even without `supports { }`. An unqualified `./gradlew kmpCompileAll` from the root fans out to every project that has the task — no root aggregator. A module with nothing registered is a no-op, never a 404.
- They depend on registered platform compilations only, never on `*KotlinMetadata` compilations — so they cannot re-introduce the [inert-module](recipes.md#gate-compilation-on-inert-modules) failures those gates disable.

In the matrix, replace `build` for compile-only jobs:

```bash
./gradlew kmpCompileAll "-Pkmptargets.targets=${{ matrix.targets }}"
```

The [`desktop-named` sample](../samples/index.md) asserts the rename-proofing: its jvm leaf is renamed to `desktop`, and `kmpCompileAll` wires the real `compileKotlinDesktop`.

## The living example

This repo runs the pattern: [`sample-matrix.yml`](https://github.com/rsicarelli/kmp-targets/blob/main/.github/workflows/sample-matrix.yml) builds the hello-world sample per host on every push.
