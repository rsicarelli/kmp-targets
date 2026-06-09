# ABI Validation & Selection

Binary-compatibility tooling — the classic [kotlinx binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator) (BCV) and Kotlin's built-in `abiValidation` — checks your public ABI against a committed reference dump (under `api/` by default). Both tools operate on the targets that **exist in the build**, which under kmp-targets means the targets the current selection registered (`selection ∩ supported`).

That creates a blind spot.

## The blind spot

The committed dumps are generated under the full set of targets. Run the ABI tool under a **narrowed** lane and the safety net silently shrinks:

- **An ABI update** (`apiDump` / `updateKotlinAbi`) regenerates the dump from only the registered targets — **dropping or mis-inferring** the unregistered targets' declarations in the committed file.
- **An ABI check** (`apiCheck` / `checkKotlinAbi`) validates only the registered subset — drift on an unregistered target's ABI is a **false-green**.

A developer on a `jvm`-only lane can break the `linuxX64` ABI, run the update task, and commit a green-looking diff. Nothing in either tool points at the interaction.

## The signal kmp-targets adds

The plugin **never depends on any ABI tool** — it only reads the dump directory. When the current selection did not register some of the targets the dumps cover, the diagnostic reports say so. The check is layout-agnostic (it understands both the merged `<project>.klib.api` header `// Targets: [...]` and any per-target subdirectory layout) and maps dumps to the **registered Gradle names**, so a [renamed jvm leaf](jvm-rename.md)'s `api/desktop/` resolves correctly.

In [`kmpTargetsDoctor`](doctor-mode.md):

```text
[!] ABI dumps not covered by this selection
    why:    the ABI dump directory holds dumps covering js, linuxX64, which the current selection did not register
    effect: the ABI check (checkKotlinAbi/apiCheck) validates only the registered subset — drift on the
            uncovered targets is a false-green; an ABI update (updateKotlinAbi/apiDump) would drop or infer them
    fix:    run the ABI update/check under the full selection, or in the lane that owns each target;
            make CI's full-selection lane run the check explicitly
```

[`kmpTargetsInfo`](kmp-targets-info.md) carries the same fact as a neutral `ABI dumps` section. **Under a full selection both reports are silent** — every dumped target is registered, so there is no gap. Zero noise in the common case.

This is **signal-only**: it never changes what registers, never fails a normal build, and never runs an ABI tool for you. It only annotates the two report surfaces you ask for explicitly.

## Configuration

| Key | Default | Effect |
|---|---|---|
| `kmptargets.abiDumpDir` | `api` | Directory the coverage check inspects (relative to each project). Set to `off` to disable the check, or point it at a custom layout. |

Resolved through the [standard selection layers](selection-layers.md) (CLI `-P`, env, the dedicated config files, `gradle.properties`).

## Recommended practice

- Run `apiDump`/`apiCheck` (or `updateKotlinAbi`/`checkKotlinAbi`) only under the **full** selection, or in the lane that owns each target.
- Make CI's **full-selection** lane run the check explicitly — that is the one place the whole ABI is validated.

See the [`abi-bcv` and `abi-builtin` samples](../samples/index.md) for runnable, end-to-end proof with both tools.
