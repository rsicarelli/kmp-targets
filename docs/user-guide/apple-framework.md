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
- **`buildTypes`** (default KGP's `NativeBuildType.DEFAULT_BUILD_TYPES` = DEBUG + RELEASE) is passed straight to KGP's `binaries.framework(buildTypes = …)` factory. The plugin never silently narrows a KGP default.
- **Ordering-immune.** `appleFramework` declared *before* or *after* `supports {}` behaves identically (it rides the `onRegistered` replay); a later `supports {}` union attaches only the delta.
- **Exports stay lazy.** A version-catalog `Provider` is realized by KGP at attach time, not at declaration.
- **One per module** in v1 — a second call, a blank name, or `on ⊄ apple` fails fast. (Multiple frameworks need a `namePrefix` strategy; a follow-up.)

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

XCFramework assembly, an unattached-framework advisory, build types via a layered property, multiple frameworks per module, and non-Apple binaries (`sharedLib`/`staticLib`/`executable`) are deliberate follow-ups.
