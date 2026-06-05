# Changelog

All notable changes to this project will be documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the project is pre-1.0, so breaking
changes may land in any release.

## [Unreleased]

### Changed (BREAKING)

- **Unified property naming under the `kmptargets.` namespace** ([#31]). The selection key is
  renamed; the hierarchy key was already on-convention and stays. This is a hard break — the old
  names are **not read at all** (no deprecated alias): a build still setting only `KMP_TARGETS`
  falls through to `defaultSelection` / the plugin default.

  | Concern | Before | After |
  |---|---|---|
  | Selection (`gradle.properties` / files / `local.properties`) | `KMP_TARGETS=...` | `kmptargets.targets=...` |
  | Selection (CLI) | `-PKMP_TARGETS=...` | `-Pkmptargets.targets=...` |
  | Selection (env) | `ORG_GRADLE_PROJECT_KMP_TARGETS` or bare `KMP_TARGETS` | `ORG_GRADLE_PROJECT_kmptargets.targets` |
  | Hierarchy template | `kmptargets.hierarchyTemplate` | `kmptargets.hierarchyTemplate` (unchanged) |

- The bare `KMP_TARGETS` **environment variable** is no longer read; the env surface is Gradle's
  native `ORG_GRADLE_PROJECT_<key>` mapping only (which now also works for
  `kmptargets.hierarchyTemplate`, which previously had no env form).

### Added

- **Dedicated root config files** ([#30]): `kmp-targets.properties` (committed, team-shared) and
  `kmp-targets.local.properties` (git-ignored personal override, Bazel `try-import` semantics) are
  the consolidation point for global configuration. Full precedence chain (highest first): CLI `-P`
  → `ORG_GRADLE_PROJECT_<key>` env → personal file → committed file → root `gradle.properties` →
  `local.properties` → `defaultSelection` → built-in. Both global keys
  (`kmptargets.targets`, `kmptargets.hierarchyTemplate`) read through the same chain.
- **Fail-loud key validation**: an unknown key in either dedicated file fails the build at
  configuration time with a "did you mean …?" suggestion.
- Both dedicated files are tracked configuration-cache inputs: edits invalidate the cache,
  untouched files keep cache hits.

[#30]: https://github.com/rsicarelli/kmp-targets/issues/30
[#31]: https://github.com/rsicarelli/kmp-targets/issues/31
