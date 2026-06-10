# Umbrella Tasks

`kmpCompileAll` and `kmpTestAll` are **opt-in, per-module lifecycle tasks** that depend on exactly the registered intersection — selection-agnostic and rename-proof. One stable task name compiles (or tests) whatever the current lane registered, in every module, under any [jvm rename](jvm-rename.md).

## Why hardcoded task lists break

Teams commonly drive compile-only CI jobs with explicit task names:

```bash
# Brittle: a snapshot of one particular selection.
./gradlew compileCommonMainKotlinMetadata compileKotlinJvm compileReleaseKotlinAndroid compileKotlinIosArm64
```

Once the selection is variable, that list has two failure modes:

- **Hard 404** — a name that exists in *no* module under the current lane (`compileReleaseKotlinAndroid` on an android-less selection) → `Task '…' not found`, the job fails before it starts.
- **Silent false-green (worse)** — under a [renamed jvm leaf](jvm-rename.md) (`targetName(jvm, "desktop")`) the real task is `compileKotlinDesktop`, so a literal `compileKotlinJvm` matches *zero* tasks and the job passes **while compiling nothing**. The test side is higher-stakes still: a hardcoded `jvmTest` never runs.

The plugin already knows exactly what registered in every module, so it can expose umbrella tasks that depend on the registered set instead:

- **`kmpCompileAll`** — compiles every registered target's main compilation.
- **`kmpTestAll`** — runs every registered target's tests (the targets that have a test task; a device-only native like `iosArm64` has none and is skipped).

## Enabling

The tasks are **opt-in** — they add dependency edges to every registered target's compile/test tasks, and most builds only want them for CI:

```properties
# kmp-targets.properties
kmptargets.umbrellaTasks=true
```

The flag reads through the same [precedence chain](selection-layers.md) as every other `kmptargets.` key (`-P`, env, `kmp-targets(.local).properties`, `gradle.properties`).

## Semantics

Both tasks are registered in **every** module the plugin is applied to (even one that never called `supports { }`), so an unqualified `./gradlew kmpCompileAll` from the root **fans out** to every project that has the task — no root aggregator, no cross-project wiring. Under a narrowed lane the umbrella simply depends on fewer tasks (a module with nothing registered is a clean no-op — never a 404), and under a renamed jvm leaf it wires the real `compileKotlinDesktop` / `desktopTest`.

They depend on registered **platform** compilations only and **never** on `compileCommonMainKotlinMetadata` (or any `*KotlinMetadata` compilation), so they cannot re-introduce the [inert-module](recipes.md#gate-compilation-on-inert-modules) or JVM-less-fragment failures — exactly the compilations build-logic deliberately disables for those modules.

## In the CI matrix

Drop them straight into the [CI matrix](ci-matrix.md): replace `build` with `kmpCompileAll` for compile-only jobs (or add `kmpTestAll` where the host can run the lane's tests):

```bash
# Stable: one name, correct in every lane and under any rename.
./gradlew kmpCompileAll "-Pkmptargets.targets=${{ matrix.targets }}"
```

## The living example

The [`desktop-named` sample](../samples/index.md) asserts the rename-proofing as a living regression gate: its jvm leaf is renamed to `desktop`, and `kmpCompileAll` still wires the real `compileKotlinDesktop`.
