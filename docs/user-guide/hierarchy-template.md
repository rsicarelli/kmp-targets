# Hierarchy Template

## The problem with KGP's default

KGP's auto-applied `applyDefaultHierarchyTemplate()` builds the *full* source-set hierarchy for whatever registers, so an iOS-only module still gets `nativeMain` **and** `appleMain` intermediates that are redundant with `iosMain`. Each redundant intermediate spawns ~8 wasteful Gradle tasks; across dozens of modules this dominates sync time ([the hidden cost of default hierarchy templates](https://dev.to/rsicarelli/the-hidden-cost-of-default-hierarchy-templates-in-kotlin-multiplatform-256a)).

## The minimal template

Because `kmp-targets` already knows each module's active target set, it applies a **minimal** custom hierarchy instead — collapsing every redundant single-child group:

| Active targets | Intermediate source sets |
|---|---|
| one iOS leaf | none (target attaches to `commonMain`) |
| iOS (≥2 leaves) | `iosMain` only — no `appleMain`, no `nativeMain` |
| iOS + macOS | `appleMain` over `iosMain` + `macosMain` — no `nativeMain` |
| iOS + Linux | `nativeMain` over `iosMain` + `linuxMain` — no `appleMain` |

The collapse rule: a group materializes a source set only when it merges **≥2 present children**; a single-child group collapses away. If that rule breaks your codebase's load-bearing `src/iosMain` dirs, see [No-Collapse Mode](no-collapse-mode.md).

It's **on by default** and applied automatically — no configuration needed.

## Opting out

Opt out when a module supplies its own `applyHierarchyTemplate { … }`, so the plugin stays out of the way (KGP's default applies again):

```properties
# kmp-targets.properties — global default (also accepted via -P, env, gradle.properties,
# local.properties — same precedence chain as kmptargets.targets)
kmptargets.hierarchyTemplate=false
```

```kotlin
// any module's build.gradle.kts — per-project override
kmpTargets { hierarchyTemplate.set(false) }
```

Precedence: **project DSL > global key > built-in default (`true`)**.

## Renamed targets

KGP's hierarchy matchers (`withJvm()`) key off the *platform type*, not the target name — so a [renamed jvm target](jvm-rename.md) (`targetName(jvm, "desktop")`) attaches to the minimal template exactly as a plain `jvm` would.

## Related

- [No-Collapse Mode](no-collapse-mode.md) — keep single-child intermediates alive
- [Why kmp-targets?](../why-kmp-targets.md) — where the hierarchy tax fits in the bigger picture
