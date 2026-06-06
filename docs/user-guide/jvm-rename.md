# Renaming the JVM Target

Codebases that grew up before the default-hierarchy era often register their JVM target under a custom name — most commonly `kotlin.jvm("desktop")` — and carry years of accumulated `src/desktopMain` source dirs, `-desktop` published artifact suffixes, and CI task names (`compileKotlinDesktop`) keyed off it. For them, adopting kmp-targets must not be a breaking rename.

`targetName` lets the jvm leaf register under that custom Gradle name:

```kotlin
kmpTargets {
    targetName(jvm, "desktop") // must come BEFORE supports { } — registration is eager and one-way
    supports { mobile + jvm }
}
```

## Semantics

- **Selection token unchanged.** Builds still select it with `jvm` (or its `desktop` alias) — `kmptargets.targets=jvm,iosArm64` registers the renamed target. Only the registered Gradle target, its source sets (`desktopMain`), and the published artifact suffix follow the custom name.
- **jvm leaf only.** `androidTarget`'s name is fixed by AGP, and native/web names are derived by KGP — renaming them would break konan/source-set conventions. `targetName` fails loud on any other leaf (or a preset), on a blank name, and when called after `supports { }` already registered the jvm leaf (a late rename could never apply).
- **Hierarchy unaffected.** KGP's hierarchy matchers (`withJvm()`) key off the platform type, not the target name, so the [minimal template](hierarchy-template.md) attaches a renamed target exactly as it would a plain `jvm`.
- **Introspection.** [`kmpTargetsInfo`](kmp-targets-info.md) surfaces the rename on the registered line: `jvm (registered as: desktop)`. Build-logic sees it through [`RegisteredTarget.gradleName`](build-logic.md#registeredtarget), so [`onRegistered`-driven wiring](build-logic.md) keeps working without knowing about the rename.

## See it running

The [`desktop-named` sample](../samples/index.md) supports `jvm + iosArm64` with the jvm leaf renamed to `desktop` — `src/desktopMain` and `compileKotlinDesktop` work as in a legacy codebase.
