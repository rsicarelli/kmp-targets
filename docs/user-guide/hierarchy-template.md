# Hierarchy

The plugin replaces KGP's default hierarchy template with a minimal one: only the intermediate source sets the registered targets need. Why: [Design](../why-kmp-targets.md#hierarchy).

## Minimal template

| Active targets | Intermediate source sets |
|---|---|
| one iOS leaf | none (target attaches to `commonMain`) |
| iOS (≥2 leaves) | `iosMain` only — no `appleMain`, no `nativeMain` |
| iOS + macOS | `appleMain` over `iosMain` + `macosMain` — no `nativeMain` |
| iOS + Linux | `nativeMain` over `iosMain` + `linuxMain` — no `appleMain` |

The collapse rule: a group materializes a source set only when it merges ≥2 present children; a single-child group collapses away. If that drops a source dir your code needs, see [Keeping intermediates](#keeping-intermediates).

On by default, applied automatically.

## Opting out

Opt out when a module supplies its own `applyHierarchyTemplate { … }` — KGP's default applies again:

```properties
# kmp-targets.properties — global default (also accepted via -P, env, gradle.properties,
# local.properties — same precedence chain as kmptargets.targets)
kmptargets.hierarchyTemplate=false
```

```kotlin
// any module's build.gradle.kts — per-project override
kmpTargets { hierarchyTemplate.set(false) }
```

Precedence: project DSL > global key > built-in default (`true`).

## Keeping intermediates

Codebases with `actual` implementations in intermediate source dirs (`src/iosMain`) break under the collapse: narrowing to a single iOS leaf (`-Pkmptargets.targets=iosArm64`) drops `iosMain` from the model and `expect` declarations stop resolving. No-collapse mode materializes a group whenever it has ≥1 present child, so `iosMain` survives:

```properties
# kmp-targets.properties — global default (same precedence chain as the other keys)
kmptargets.hierarchyCollapse=false
```

```kotlin
// any module's build.gradle.kts — per-project override; set BEFORE supports { }
kmpTargets {
    collapseHierarchy.set(false)
    supports { appleMobile }
}
```

Precedence mirrors `hierarchyTemplate`: project DSL > global key > built-in default (`true`, collapse).

Semantics:

- Single-child chains materialize fully (`nativeMain → appleMain → iosMain` for one iOS leaf). The empty intermediates are harmless — no code, nothing to resolve.
- Empty groups are still dropped; ungrouped leaves (jvm/android/web) still never form groups.
- The knob never changes what registers — only which intermediate source sets materialize. It is a no-op when `hierarchyTemplate` resolves to `false`.

The [`pinned-intermediates` sample](../samples/index.md) is an `appleMobile` module with collapse off.

## Renamed targets

KGP's hierarchy matchers (`withJvm()`) key off the platform type, not the target name — a [renamed jvm target](selection-dsl.md#renaming-the-jvm-target) attaches to the minimal template exactly as a plain `jvm` would.
