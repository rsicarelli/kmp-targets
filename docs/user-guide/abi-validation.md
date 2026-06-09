# ABI Validation & Selection

Binary-compatibility tooling — the classic [kotlinx binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator) (BCV) and Kotlin's built-in `abiValidation` — checks your public ABI against a committed reference dump. Both tools operate on the targets that **exist in the build**, which under kmp-targets means the targets the current selection registered (`selection ∩ supported`).

That creates a blind spot.

## The blind spot

The committed dumps are generated under the full set of targets. Run an ABI task under a **narrowed** lane and it can only act on the registered targets:

- **An ABI update** (`apiDump` / `updateKotlinAbi`) regenerates the dump from only the registered targets — **dropping** the unregistered targets' declarations from the committed file.
- **An ABI check** (`apiCheck` / `checkKotlinAbi`) covers only the registered subset. The two tools then diverge:
    - Kotlin's **built-in** `checkKotlinAbi` passes **green** — a silent false-green on the unregistered targets.
    - **BCV** `apiCheck` fails **loud** (its `klibApiCheck` diffs the committed klib dump against nothing) and suggests an `apiDump` that would *drop* those targets.

A developer on a `jvm`-only lane can break the `linuxX64` ABI and — with the built-in tool — commit a green-looking diff.

## The signal kmp-targets adds

The plugin **depends on no ABI tool**. It hooks the ABI lifecycle tasks **by name** (`apiDump`, `apiCheck`, `updateKotlinAbi`, `checkKotlinAbi`) — the only approach that catches *both* tools, since the built-in one has no separate plugin id — and gates on the diff it already owns: `resolvedSupported() − registered()`. When that is non-empty, the run under-covers, and the plugin warns at the moment the task runs:

```
kmp-targets: ':lib' is running 'checkKotlinAbi' under a narrowed selection — it covers only the
registered targets, so js, linuxX64 are NOT dumped/validated by this run. Run it under the full
selection (or the lane that owns each target); your team's full-selection CI lane is the safety net.
(strict mode fails this.)
```

It is deliberately **signal-only and best-effort**, not a guarantee:

- It compiles nothing, parses no dump files, and reads no `api/` directory — just a set difference the plugin already computes. Configuration-cache safe (the `doFirst` captures only strings).
- Under a **full** selection `supported − registered` is empty, so it is silent — zero noise in the common case.
- It does **not** replace the ABI check. Your team's full-selection CI lane is the authoritative gate; this exists so a local narrowed run is never silent.
- Under [strict mode](advisories.md) (`kmptargets.strict=true`) the warning becomes a build failure.

No new configuration key, no opt-in: if an ABI task exists and the lane is narrowed, you get the signal.

## Recommended practice

- Run `apiDump`/`apiCheck` (or `updateKotlinAbi`/`checkKotlinAbi`) under the **full** selection, or in the lane that owns each target.
- Make CI's **full-selection** lane run the check explicitly — that is the one place the whole ABI is validated.

See the [`abi-bcv` and `abi-builtin` samples](../samples/index.md) for runnable, end-to-end proof with both tools — including the green-vs-loud contrast.
