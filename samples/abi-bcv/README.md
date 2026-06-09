# `abi-bcv` — kmp-targets × kotlinx binary-compatibility-validator

A standalone, single-module KMP library that supports `jvm + linuxX64 + js`, applies the classic
[kotlinx binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator)
(BCV), and commits BCV's ABI dumps under [`api/`](api). It exists to make one interaction concrete
and testable: **target selection narrows what BCV can see.**

## The blind spot

BCV operates on the targets that *exist* in the build — under kmp-targets, the targets the current
selection registered (`selection ∩ supported`). The committed dumps here were generated under the
**full** selection (`kmptargets.targets=jvm,linuxX64,js`, in [`kmp-targets.properties`](kmp-targets.properties)):

- [`api/abi-bcv.api`](api/abi-bcv.api) — the JVM ABI.
- [`api/abi-bcv.klib.api`](api/abi-bcv.klib.api) — the merged KLIB ABI, headed `// Targets: [js, linuxX64]`.

Run BCV under a **narrowed** lane and it can only act on the registered targets:

```bash
./gradlew apiCheck -Pkmptargets.targets=jvm   # only jvm registered
```

BCV's `klibApiCheck` then compares the committed klib dump (js, linuxX64) against *nothing* and
**fails loudly**, suggesting you run `apiDump` to "fix" it — which under this lane would **drop** the
js/linuxX64 declarations from the committed dump. Different flavour from the built-in tool (which
passes green; see [`abi-builtin`](../abi-builtin)), same root cause.

## The signal kmp-targets adds

The plugin never depends on BCV. It hooks the ABI lifecycle tasks **by name** and, when this run's
selection leaves part of the supported set unregistered (`supported − registered` ≠ ∅), warns at the
moment the task runs:

```
kmp-targets: ':' is running 'apiDump' under a narrowed selection — it covers only the registered
targets, so js, linuxX64 are NOT dumped/validated by this run. Run it under the full selection (or the
lane that owns each target); your team's full-selection CI lane is the safety net. (strict mode fails this.)
```

This fires for `apiDump`/`apiCheck` (and the built-in `updateKotlinAbi`/`checkKotlinAbi`). Under the
**full** selection `supported − registered` is empty, so it stays silent — zero noise. With
`kmptargets.strict=true` the warning becomes a build failure.

## Recommended practice

The point is **not** to replace BCV's check — your team's CI does that. The point is to not be in the
dark locally:

- Run `apiDump`/`apiCheck` under the **full** selection, or in the lane that owns each target.
- Make CI's **full-selection** lane run `apiCheck` explicitly — that is the authoritative gate.

```bash
task publish-local
./gradlew -p samples/abi-bcv apiCheck                       # full selection (recommended lane) → clean
./gradlew -p samples/abi-bcv apiDump -Pkmptargets.targets=jvm   # narrowed → kmp-targets warns it under-covers
```

## Regenerating the dumps

```bash
./gradlew -p samples/abi-bcv apiDump   # full selection; needs the Kotlin/Native toolchain for linuxX64
```

> KLIB validation is opt-in in BCV — see the `apiValidation { klib { enabled = true } }` block in
> [`build.gradle.kts`](build.gradle.kts). Without it, only the JVM `.api` is dumped.
