# `abi-bcv` — kmp-targets × kotlinx binary-compatibility-validator

A standalone, single-module KMP library that supports `jvm + linuxX64 + js`, applies the classic
[kotlinx binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator)
(BCV), and commits BCV's ABI dumps under [`api/`](api). It exists to make one interaction concrete
and testable: **target selection narrows what BCV can see.**

## The blind spot

BCV operates on the targets that *exist* in the build. Under kmp-targets, that is the targets the
current selection registered (`selection ∩ supported`). The committed dumps here were generated under
the **full** selection (`kmptargets.targets=jvm,linuxX64,js`, in [`kmp-targets.properties`](kmp-targets.properties)):

- [`api/abi-bcv.api`](api/abi-bcv.api) — the JVM ABI.
- [`api/abi-bcv.klib.api`](api/abi-bcv.klib.api) — the merged KLIB ABI, headed `// Targets: [js, linuxX64]`.

Run BCV under a **narrowed** lane and the safety net quietly shrinks:

```bash
# A jvm-only lane: only the JVM target registers.
./gradlew apiCheck   -Pkmptargets.targets=jvm   # validates only the JVM ABI — js/linuxX64 drift sails through
./gradlew apiDump    -Pkmptargets.targets=jvm   # regenerates the dumps from jvm only — js/linuxX64 coverage is dropped/inferred
```

Nothing in BCV points at the interaction: a developer on the jvm lane can break the linuxX64 ABI,
run `apiDump`, and commit a green-looking diff.

## What kmp-targets does about it (signal-only, no BCV dependency)

The plugin never depends on BCV — it only reads the `api/` directory. When the current selection did
not register some of the targets the dumps cover, the diagnostic reports say so:

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

`kmpTargetsInfo -Pkmptargets.targets=jvm` carries the same fact as a neutral `ABI dumps` section.
Under the full selection both reports are silent — every dumped target is registered, so there is no
gap (zero noise in the common case). The directory is configurable / disable-able via
`kmptargets.abiDumpDir` (default `api`; set to `off` to disable the check).

## Recommended practice

- Run `apiDump`/`apiCheck` only under the **full** selection, or in the lane that owns each target.
- Make CI's **full-selection** lane run `apiCheck` explicitly — that is the one place the whole ABI
  is validated.

## Regenerating the dumps

```bash
task publish-local
./gradlew -p samples/abi-bcv apiDump     # full selection (from kmp-targets.properties); needs the Kotlin/Native toolchain for linuxX64
```

> KLIB validation is opt-in in BCV — see the `apiValidation { klib { enabled = true } }` block in
> [`build.gradle.kts`](build.gradle.kts). Without it, only the JVM `.api` is dumped.
