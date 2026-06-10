# ABI Validation & Selection

Binary-compatibility tooling — [kotlinx binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator) (BCV) and Kotlin's built-in `abiValidation` — checks the public ABI against a committed reference dump. Both operate on the targets that exist in the build, which under kmp-targets means the registered set (`selection ∩ supported`).

## The coverage gap

Committed dumps are generated under the full target set. An ABI task under a narrowed lane acts on the registered subset only:

- **An ABI update** (`apiDump` / `updateKotlinAbi`) regenerates the dump from the registered targets, dropping the unregistered targets' declarations from the committed file.
- **An ABI check** (`apiCheck` / `checkKotlinAbi`) covers the registered subset. The two tools diverge: the built-in `checkKotlinAbi` passes without validating the missing targets; BCV's `apiCheck` fails and suggests an `apiDump` that would drop them.

On a `jvm`-only lane, a change that breaks the `linuxX64` ABI passes the built-in check.

## The signal kmp-targets adds

The plugin depends on no ABI tool. It hooks the ABI lifecycle tasks by name (`apiDump`, `apiCheck`, `updateKotlinAbi`, `checkKotlinAbi`) — the only approach that covers both tools, since the built-in one has no separate plugin id — and gates on `resolvedSupported() − registered()`. When that diff is non-empty, the run under-covers and the plugin warns when the task runs:

```
kmp-targets: ':lib' is running 'checkKotlinAbi' under a narrowed selection — it covers only the
registered targets, so js, linuxX64 are NOT dumped/validated by this run. Run it under the full
selection (or the lane that owns each target); your team's full-selection CI lane is the safety net.
(strict mode fails this.)
```

The gate is ABI-group-aware: a leaf is flagged only when its whole ABI group has no registered representative. With `iosArm64 + iosSimulatorArm64` supported but only the simulator registered, the iOS ABI surface is still dumped (KLIB dumps share declarations across a family), so `iosArm64` is not flagged. Native targets group by konan family; `jvm`, `androidTarget`, and each web leaf are distinct ABIs.

Signal-only, best-effort:

- It compiles nothing, parses no dump files, reads no `api/` directory — a set difference the plugin already computes. Configuration-cache safe.
- Under a full selection the diff is empty and it is silent.
- It does not replace the ABI check; the full-selection CI lane is the authoritative gate. Under [strict mode](advisories.md#strict-mode) the warning becomes a failure.

No configuration key, no opt-in: an ABI task plus a narrowed lane produces the signal.

## Recommended practice

- Run `apiDump`/`apiCheck` (or `updateKotlinAbi`/`checkKotlinAbi`) under the full selection, or in the lane that owns each target.
- Make CI's full-selection lane run the check explicitly.

The [`abi-bcv` and `abi-builtin` samples](../samples/index.md) run the gap end-to-end with both tools.
