# Selection Layers

The selection is global — one switch narrows the whole build. Its sources form three layers in increasing priority:

| Layer | Sources | Who sets it |
|---|---|---|
| **Committed default** | `kmp-targets.properties` (and legacy root `gradle.properties`) | the team, in git |
| **Personal override** | `kmp-targets.local.properties` (and legacy `local.properties`) — git-ignored | each developer, per machine |
| **Per-invocation** | `-Pkmptargets.targets=…` CLI flag, `ORG_GRADLE_PROJECT_kmptargets.targets` env | this one build — a terminal run, a CI job |

`kmptargets.targets` is the **single canonical key** across every layer — the string you commit, the string you override locally, and the string a [CI matrix](ci-matrix.md) passes per job are the same vocabulary.

## Exact precedence (highest first)

1. `-Pkmptargets.targets=...` on the CLI
2. `ORG_GRADLE_PROJECT_kmptargets.targets` environment variable
3. `kmp-targets.local.properties` (per-developer, git-ignored)
4. `kmp-targets.properties` (committed, team-shared)
5. the **root** `gradle.properties` (a *subproject's* `gradle.properties` is **not** a source — Gradle only reads root-level project properties)
6. `local.properties` (per-developer, git-ignored)

When no source provides a value, two **fallbacks** (not overrides — anything above beats them) apply: a project-wide `defaultSelection` set from build-logic, then the plugin default — every target the plugin knows about.

The rarer `-Dorg.gradle.project.kmptargets.targets` and `~/.gradle/gradle.properties` forms resolve at the `gradle.properties` layer, below the dedicated files. Why the dedicated files outrank `gradle.properties`: [Design](../why-kmp-targets.md#selection-model).

## The config files

```properties
# kmp-targets.properties — committed, team-shared
kmptargets.targets=jvm,iosArm64
kmptargets.hierarchyTemplate=true
# kmptargets.hierarchyCollapse=false   # see "Hierarchy"
# kmptargets.strict=true               # see "Advisories & Strict Mode"
# kmptargets.umbrellaTasks=true        # see "CI"
```

`kmp-targets.local.properties` (git-ignored) is optional: absent it's ignored; present, its keys override the committed file's. Both files accept only known `kmptargets.*` keys — an unknown key fails the build with a "did you mean …?" suggestion. Both are tracked configuration-cache inputs: editing one invalidates the cache, leaving them untouched keeps cache hits.

## Selection grammar

```properties
kmptargets.targets=android,iosArm64        # explicit list
kmptargets.targets=appleMobile             # preset (iosArm64 + iosSimulatorArm64 + iosX64)
kmptargets.targets=appleMobile,-iosArm64   # preset minus a leaf
kmptargets.targets=apple,+android          # preset plus an addition
kmptargets.targets=ANDROID, ios-arm64      # aliases + case-insensitive
```

Unknown tokens fail the build at configuration time with a "did you mean …?" suggestion ([why the parser is strict](../why-kmp-targets.md#selection-model)). Bare Apple sub-family names (`ios`, `macos`, `watchos`, `tvos`) are rejected with a hint pointing at the relevant leaf or `appleX` preset.

The full preset and leaf vocabulary lives in the [Targets Reference](targets-reference.md).

## Diagnosing a surprising selection

[`kmpTargetsInfo`](diagnostics.md#kmptargetsinfo) prints the resolved selection and the winning source by name — `command line (-Pkmptargets.targets)`, `kmp-targets.local.properties`, etc. Values from `-Dorg.gradle.project.kmptargets.targets` and `~/.gradle/gradle.properties` are indistinguishable from root `gradle.properties`; all three report as the fused `gradle.properties (...)` layer.
