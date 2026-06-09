# `abi-builtin` — kmp-targets × Kotlin's built-in ABI validation

The sibling of [`abi-bcv`](../abi-bcv), using the ABI tool that ships **inside** the Kotlin Gradle
plugin (no third-party plugin) — `kotlin { abiValidation { } }` (experimental in KGP 2.3.21), with
the `updateKotlinAbi` / `checkKotlinAbi` tasks. The selection × ABI blind spot, and the plugin's
signal-only response to it, are identical; this sample proves the kmp-targets annotation is
**tool-agnostic**.

## The blind spot (same as abi-bcv)

The committed dumps under [`api/`](api) were generated under the **full** selection
(`kmptargets.targets=jvm,linuxX64,js`):

- [`api/abi-builtin.api`](api/abi-builtin.api) — the JVM ABI.
- [`api/abi-builtin.klib.api`](api/abi-builtin.klib.api) — the merged KLIB ABI, headed `// Targets: [js, linuxX64]`.

Under a narrowed lane only the registered targets are seen:

```bash
./gradlew checkKotlinAbi  -Pkmptargets.targets=jvm   # validates only the JVM ABI
./gradlew updateKotlinAbi -Pkmptargets.targets=jvm   # regenerates from jvm only — js/linuxX64 coverage drops/infers
```

## What kmp-targets does about it

```bash
./gradlew kmpTargetsDoctor -Pkmptargets.targets=jvm
```
```
[!] ABI dumps not covered by this selection
    why:    the ABI dump directory holds dumps covering js, linuxX64, which the current selection did not register
    effect: the ABI check (checkKotlinAbi/apiCheck) validates only the registered subset — drift on the
            uncovered targets is a false-green; an ABI update (updateKotlinAbi/apiDump) would drop or infer them
    fix:    run the ABI update/check under the full selection, or in the lane that owns each target;
            make CI's full-selection lane run the check explicitly
```

Under the full selection the report is silent. The same `kmpTargetsInfo` `ABI dumps` section and the
`kmptargets.abiDumpDir` knob (default `api`, `off` to disable) apply — see [`abi-bcv`](../abi-bcv)
for the full write-up.

## Regenerating the dumps

```bash
task publish-local
./gradlew -p samples/abi-builtin updateKotlinAbi   # full selection; needs the Kotlin/Native toolchain for linuxX64
```

> KLIB validation is opt-in — see `abiValidation { klib.enabled.set(true) }` in
> [`build.gradle.kts`](build.gradle.kts).
