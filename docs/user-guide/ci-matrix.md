# CI Matrix

## The cost asymmetry

KMP CI has a sharp cost asymmetry: on **private repositories**, GitHub Actions macOS runners bill at roughly **10×** the Ubuntu per-minute rate — and Apple/native compilation, the part that *requires* macOS, is the dominant slice of total CI time. The cost-aware pattern is an **OS × selection matrix**: each runner compiles only the subset of targets it can host.

`kmp-targets` is purpose-built for this. The matrix passes a host-appropriate selection per job — and the values are **literally the same strings** a developer puts in `kmp-targets.local.properties` or `-Pkmptargets.targets` locally. CI never grows its own divergent selection language.

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

!!! tip "Compile-only jobs"
    For jobs that only need to compile (no tests, no full `build`), replace `build` with the opt-in [`kmpCompileAll` umbrella task](umbrella-tasks.md) — one stable name that follows the lane's registered set and survives a [jvm rename](jvm-rename.md).

## Host → target mapping

| Runner | `kmptargets.targets` | Rationale |
|---|---|---|
| `ubuntu-latest` | `jvm,android,js,wasmJs,wasmWasi,linuxX64,linuxArm64,androidNative` | the cheap runner — every non-Apple, non-MinGW target cross-compiles here (`android` requires AGP in your build; `web` is the `js,wasmJs,wasmWasi` preset) |
| `macos-latest` | `apple` (or `appleMobile` on PRs) | the only host that can compile/link Apple targets; keep the expensive runner's set minimal |
| `windows-latest` | `mingwX64` (alias `windows`) | the only host for MinGW |

## Notes

- **Same vocabulary, no drift.** Matrix values are plain [selection grammar](selection-layers.md#selection-grammar) strings — presets, leaves, and `-` exclusions all work. What CI builds is what [`kmpTargetsInfo`](kmp-targets-info.md) prints locally for the same value.
- **Env form.** `ORG_GRADLE_PROJECT_kmptargets.targets` as a job `env:` is equivalent (dotted env keys work on GitHub Actions); `-P` is the canonical recommendation. Both sit above the committed `kmp-targets.properties` in [precedence](selection-layers.md), so a per-job value always wins over the repo default.
- **Cost tier (optional).** Restrict the macOS job to `appleMobile` on `pull_request` and run the full `apple` preset only on `push` to `main` — the matrix `targets` value is the only thing that changes.
- **Strict mode pairs well.** Set [`kmptargets.strict=true`](advisories.md#strict-mode) on jobs whose selection the host can fully compile — a misconfigured module then fails the job instead of silently building nothing.
- **Configuration cache.** Each distinct selection is a distinct configuration-cache key; matrix jobs with different selections won't share an entry. Expected, not a regression — the same bounded cost described in [Why kmp-targets?](../why-kmp-targets.md#the-trade-off-stated-plainly).

## The living example

This repo runs the pattern for real: [`sample-matrix.yml`](https://github.com/rsicarelli/kmp-targets/blob/main/.github/workflows/sample-matrix.yml) builds the hello-world sample per host — the macOS job is the only place Apple targets get genuinely compiled — so the example above can't rot.
