# No-Collapse Mode

## The use case: load-bearing intermediates

The [minimal template's](hierarchy-template.md) collapse rule — a group materializes only when it merges **≥2 present children** — is right for new code. But established codebases have **load-bearing intermediate source dirs**: dozens of modules with `src/iosMain` holding `actual` implementations.

For them, narrowing the selection to a single iOS leaf (`-Pkmptargets.targets=iosArm64`, the everyday "build for device only" move) silently drops `iosMain` from the model, and the build breaks with unresolved `expect` declarations.

## The opt-out

No-collapse mode materializes a group whenever it has **≥1 present child**, so `iosMain` survives a single-iOS-leaf selection:

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

Precedence mirrors `hierarchyTemplate`: **project DSL > global key > built-in default (`true`, collapse — the minimal tree)**.

## Semantics, precisely

- Single-child chains materialize fully (`nativeMain → appleMain → iosMain` for one iOS leaf). The inert empty intermediates are harmless — no code, no `expect`/`actual` to resolve.
- Empty groups are still dropped, and ungrouped leaves (jvm/android/web) still never form groups.
- The knob **never changes what registers** — only which intermediate source sets materialize.
- It is a documented no-op when [`hierarchyTemplate`](hierarchy-template.md) resolves to `false` — KGP's default template owns the tree then.

!!! note "Possible future extension"
    Force-materializing *named* groups only (e.g. just `ios`, avoiding the inert parents) is a recognized refinement, currently out of scope.

## See it running

The [`pinned-intermediates` sample](../samples/index.md) is an `appleMobile` module with collapse off — narrow it to one leaf and `iosMain` stays in the model.
