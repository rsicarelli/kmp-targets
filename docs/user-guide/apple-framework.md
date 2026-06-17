# Apple Framework

Modules that ship a Kotlin framework to an iOS/macOS app usually hand-roll the same block: pick native targets from ad-hoc properties, loop them, call `binaries.framework { baseName = …; export(…) }`. The plugin already owns that loop's input — the registration log ([`onRegistered`](build-logic.md)) — so it can attach the framework for you.

It does that **without copying KGP's `Framework` API**. The plugin owns only the loop, the base name, the build types, and the Apple-subset filter; the configure block is the *real* `org.jetbrains.kotlin.gradle.plugin.mpp.Framework`. Nothing here is reviewed on a Kotlin release — a new framework option shows up in your block for free.

## `appleFramework`

Declare the framework once; it attaches to every registered Apple leaf:

```kotlin
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

kmpTargets {
    supports { appleMobile }

    appleFramework("KotlinShared") {           // this: Framework (real KGP)
        isStatic = false                       // KGP
        export(libs.shared.core)               // KGP export(Any): catalog Provider / project / "g:a:v"
        binaryOption("bundleId", "com.example.shared")
    }
}
```

- **`baseName`** is the first argument; KGP's `namePrefix` stays `""`, so link-task names (`linkDebugFrameworkIosSimulatorArm64`) and the `embedAndSign…` contract match plain KGP byte-for-byte.
- **`on`** (default `apple`) narrows which Apple leaves attach, using the same target algebra as `supports`: `appleFramework("KotlinShared", on = KmpTargetSet.appleMobile) { … }`. An `on` not ⊆ `apple` fails at declaration, naming the offending ids.
- **`buildTypes`** (default KGP's `NativeBuildType.DEFAULT_BUILD_TYPES` = DEBUG + RELEASE) is passed straight to KGP's `binaries.framework(buildTypes = …)` factory. The plugin never silently narrows a KGP default — the declaration is the ceiling. A lane can narrow it globally with [`kmptargets.framework.buildTypes`](#build-types-per-lane) (`effective = property ∩ declared`).
- **Ordering-immune.** `appleFramework` declared *before* or *after* `supports {}` behaves identically (it rides the `onRegistered` replay); a later `supports {}` union attaches only the delta.
- **Exports stay lazy.** A version-catalog `Provider` is realized by KGP at attach time, not at declaration.
- **One per module** in v1 — a second call, a blank name, or `on ⊄ apple` fails fast. (Multiple frameworks need a `namePrefix` strategy; a follow-up.)
- **Attaches to nothing? You're warned.** If the current selection registers no Apple target in the framework's `on` scope (a jvm-only lane, or an `on` narrower than what registered), the framework silently never builds and an Xcode consumer fails downstream — so the plugin emits the [framework-without-an-Apple-target advisory](advisories.md#framework-without-an-apple-target) (a configuration failure under `kmptargets.strict`). [`kmpTargetsInfo`](diagnostics.md#kmptargetsinfo) and [`kmpTargetsDoctor`](diagnostics.md#kmptargetsdoctor) render the declared name and the leaves it attached to.

## XCFramework assembly

Set `xcframework = true` to also bundle the registered slices into one `.xcframework` (the artifact an Xcode app links):

```kotlin
kmpTargets {
    supports { appleMobile }
    appleFramework("KotlinShared", xcframework = true) { isStatic = false }
}
```

KGP registers `assembleKotlinSharedDebugXCFramework`, `assembleKotlinSharedReleaseXCFramework`, and the parent `assembleKotlinSharedXCFramework`.

- **Opt-in**, matching the umbrella-task precedent — assembly tasks add edges, so they're off by default.
- **Lazy**: the `XCFrameworkConfig` is created on the first Apple attach, so a selection that registers no Apple leaf (e.g. a jvm-only lane) wires **zero** `assemble…XCFramework` tasks.
- **Invariant-safe by construction**: KGP requires every framework in an XCFramework to share a `baseName` and match on `buildType` — both come from the single declaration, so there's nothing to maintain by hand. `buildTypes` flows through (`buildTypes = listOf(NativeBuildType.DEBUG)` produces only the Debug assemble variant).
- **macOS to run, host-blind to register**: the assemble tasks shell out to `xcodebuild`, so they only *execute* on a macOS host. Registration is host-independent (same as the link tasks), so your selection stays host-blind.

## Build types per lane

Build type is **lane-shaped, not module-shaped**: local dev wants DEBUG-only links (fast), the release lane wants RELEASE. So which `NativeBuildType`s a framework links is a global, layered input — `kmptargets.framework.buildTypes` — resolved through the *same* [selection-sources chain](selection-layers.md) as `kmptargets.targets` (CLI `-P` → `ORG_GRADLE_PROJECT_*` env → `kmp-targets.local.properties` → `kmp-targets.properties` → `gradle.properties`), validated against the key registry, with the same did-you-mean on a typo.

```properties
# kmp-targets.properties — team default: fast local links
kmptargets.framework.buildTypes=debug
```

```bash
# the release lane overrides it, exactly like the targets key
./gradlew assembleKotlinSharedXCFramework -Pkmptargets.framework.buildTypes=release
```

- **Effective = property ∩ declared** — the `selection ∩ supported` shape on the build-type axis. The property only ever **narrows** what the module declared; it can never link a build type the declaration didn't list. Absent property → the declared value wins (no silent narrowing of a KGP default).
- **Grammar**: a comma-separated, case-insensitive list of `debug` / `release` (KGP's closed `NativeBuildType` enum). A junk value (`relese`) fails the build with a did-you-mean.
- **Drives both** the per-buildType framework link tasks **and** the XCFramework assemble variants: `=debug` leaves only `linkDebugFramework…` and `assemble…DebugXCFramework`.
- **Disjoint → nothing links.** A module that declares `buildTypes = listOf(NativeBuildType.DEBUG)` under a `=release` lane has an empty effective set: no binary (or XCFramework slice) links, and the plugin emits the [framework-build-types-disjoint advisory](advisories.md#framework-build-types-disjoint) (a configuration failure under `kmptargets.strict`).
- **Surfaced.** [`kmpTargetsInfo`](diagnostics.md#kmptargetsinfo) prints the effective build types, the winning origin layer, and the declared set when a lane narrowed it.

## `onAppleTarget` — the no-magic primitive

`appleFramework` is thin sugar over `onAppleTarget`, which hands you the live KGP `KotlinNativeTarget` for every registered Apple leaf. Reach for it for anything that isn't a single framework — multiple binaries, cinterops, linker options:

```kotlin
kmpTargets {
    supports { appleMobile }
    onAppleTarget {                            // this: KotlinNativeTarget (real KGP)
        binaries.framework("KotlinShared") { isStatic = false }
        compilations.getByName("main").cinterops.create("analytics")
    }
}
```

Same replay/ordering guarantees as `onRegistered`; fires only when KGP is applied; runs at configuration time only (don't hold the target from a task action).

## From build-logic

Both are plain extension calls — no type-safe receiver to bypass — so a convention plugin uses them directly, and `on` already takes a raw `KmpTargetSet`:

```kotlin
import com.rsicarelli.kmptargets.KmpTargetsExtension
import com.rsicarelli.kmptargets.model.KmpTargetSet

extensions.configure<KmpTargetsExtension> {
    appleFramework("KotlinShared", on = KmpTargetSet.appleMobile) { isStatic = false }
    supports(KmpTargetSet.appleMobile)
}
```

## Out of scope

Per-module build-type property variants, mapping Xcode's `CONFIGURATION` env var, and custom (non-DEBUG/RELEASE) build types are out of scope (KGP's enum is closed). Multiple frameworks per module (and aggregating several into one XCFramework), and non-Apple binaries (`sharedLib`/`staticLib`/`executable`) are deliberate follow-ups.
