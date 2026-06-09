# `abi-builtin` — kmp-targets × Kotlin's built-in ABI validation

The sibling of [`abi-bcv`](../abi-bcv), using the ABI tool that ships **inside** the Kotlin Gradle
plugin (no third-party plugin) — `kotlin { abiValidation { } }` (experimental in KGP 2.3.21), with
the `updateKotlinAbi` / `checkKotlinAbi` tasks. The selection blind spot is the same; this sample
proves the kmp-targets signal is **tool-agnostic** (it hooks ABI tasks by name).

## The blind spot — and why it's worse here

The committed dumps under [`api/`](api) were generated under the **full** selection
(`kmptargets.targets=jvm,linuxX64,js`):

- [`api/abi-builtin.api`](api/abi-builtin.api) — the JVM ABI.
- [`api/abi-builtin.klib.api`](api/abi-builtin.klib.api) — the merged KLIB ABI, headed `// Targets: [js, linuxX64]`.

Under a narrowed lane only the registered targets are seen:

```bash
./gradlew checkKotlinAbi -Pkmptargets.targets=jvm   # only jvm registered
```

Unlike BCV (which fails loudly), the built-in `checkKotlinAbi` here passes **green** — silently
validating only the JVM ABI while js/linuxX64 drift would sail through. That **false-green** is the
most dangerous shape of the blind spot, and the kmp-targets signal is the only thing that flags it:

```
> Task :checkKotlinAbi
kmp-targets: ':' is running 'checkKotlinAbi' under a narrowed selection — it covers only the registered
targets, so js, linuxX64 are NOT dumped/validated by this run. Run it under the full selection (or the
lane that owns each target); your team's full-selection CI lane is the safety net. (strict mode fails this.)

BUILD SUCCESSFUL
```

Under the full selection `supported − registered` is empty and the signal is silent. With
`kmptargets.strict=true` it becomes a build failure. The hook is configuration-cache safe (the run
above stores and reuses a config-cache entry).

## Recommended practice

Same as [`abi-bcv`](../abi-bcv): run the ABI update/check under the full selection (or the owning
lane), and let CI's full-selection lane be the authoritative gate.

## Regenerating the dumps

```bash
task publish-local
./gradlew -p samples/abi-builtin updateKotlinAbi   # full selection; needs the Kotlin/Native toolchain for linuxX64
```

> KLIB validation is opt-in — see `abiValidation { klib.enabled.set(true) }` in
> [`build.gradle.kts`](build.gradle.kts).
