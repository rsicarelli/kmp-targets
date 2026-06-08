# Doctor mode

`kmpTargetsInfo` is a neutral *state dump* — "what did the plugin decide?". `kmpTargetsDoctor` is the *triage* surface — **"what's wrong, why, and how do I fix it?"**. Every project the plugin is applied to gets a `kmpTargetsDoctor` task (group `help`):

```bash
./gradlew :feature-mobile:kmpTargetsDoctor -q
```

A healthy module reports a clean bill:

```console
kmp-targets doctor — :jvm-tools

✓ clean bill of health — registered: jvm
```

A module that hit a trap gets one `[!]` block per finding — the cause, the effect, and the remedy:

```console
kmp-targets doctor — :jvm-tools

[!] selection matches nothing supported
    why:    the selection iosArm64 matches none of the supported set jvm
    effect: this module registers no targets
    fix:    widen the selection or this module's supports { } so they overlap

[!] inert module
    why:    declared supports { } but registered zero targets
    effect: KGP still materializes the commonMain metadata compilation, which fails with no platform targets
    fix:    gate on registered().isEmpty() in build-logic
```

## What it explains

Doctor **renders, it does not own predicates**: every finding is keyed off the *same* decision the matching [advisory](advisories.md) uses, so the report can never disagree with what actually happened. It just explains it where you start debugging, with the fix attached.

| Finding | The trap it explains |
|---|---|
| `selection matches nothing supported` | [empty overlap](advisories.md#empty-overlap) — a typo'd or over-narrow lane |
| `inert module` | [inert module (#71)](advisories.md#inert-modules) — zero registrations, doomed commonMain metadata compilation |
| `JVM-less commonMain` | [native-only metadata (#72)](advisories.md#native-only-metadata) — `@JvmInline` rejected while klibs compile |
| `androidTarget skipped` | [android without AGP (#51)](advisories.md#android-target-without-agp) — the leaf that didn't register |
| `not compilable on this host` | [host compatibility (#32)](advisories.md#host-compatibility) — registered, but this host can't build it |
| `deprecated targets registered` | [deprecated targets (#43)](advisories.md#deprecated-targets) |
| `note: jvm (registered as: …)` | the leaf registered under a [custom name](jvm-rename.md) — context, not a problem |

For the commonMain-KSP-needs-a-native-target trap there is intentionally **no finding**: its trigger ("does this module run a commonMain metadata-route processor?") has a conjunct the plugin cannot observe, so it stays a build-logic [recipe](recipes.md#commonmain-ksp-needs-a-native-target) gating on `registered(KmpTargetSet.native).isEmpty()` — not something doctor can detect.

## Project-edge closure

Beyond the single module, doctor flags **project-edge closure gaps** (the failure described in [Selection vs the dependency graph](recipes.md#selection-vs-the-dependency-graph)): a `project(...)` dependency that registers none of the leaves this module needs, so this module's compilations for those leaves can't resolve the dependency's artifacts.

```console
kmp-targets doctor — :app-mobile

✓ clean bill of health — registered: iosArm64, jvm

Project-edge closure (project dependencies only)
[!] :app-mobile → :lib-jvm
    missing: iosArm64 — the dependency registers none of these leaves this module needs
    fix:     widen :lib-jvm's supports { } (or the selection) to register them
  limits: external (non-project) dependencies are invisible; the android→jvm fallback is approximate
```

This crosses the project boundary by **file, never by reading a peer project's model** — each module emits its own primitives via `kmpTargetsDoctorData`, and the dependent resolves those files through a lenient artifact view. That keeps it [configuration-cache](../user-guide/selection-layers.md) and **Isolated Projects** safe; it runs unchanged under `org.gradle.unsafe.isolated-projects=true`.

!!! warning "Two permanent limits — best-effort, not a correctness guarantee"
    1. **No external-dependency coverage.** Doctor sees only `project(...)` edges. The canonical android→jvm-fallback pain comes from **external** `jvm`-only libraries, which it cannot model at all — so co-selecting `jvm` with `android` ([the rule](recipes.md#the-asymmetric-case-android-and-the-jvm-fallback)) remains the real fix.
    2. **The android→jvm fallback is approximate.** Doctor hardcodes "an `androidTarget` consumer is satisfied by a `jvm`-only producer" — it only approximates Gradle's real attribute matching, not reproduces it.

## Properties of the task

- **Read-only** — like `kmpTargetsInfo`, it never registers targets; running it cannot change the build.
- **Configuration-cache compatible** — the second run is a cache hit.
- **Per-module** — run it on the module you're diagnosing (`:feature-mobile:kmpTargetsDoctor`).
- **Runs even when the module is inert** — KGP logs its own "No Kotlin Targets Declared" error, but doctor still prints the explanation of *why*.

!!! tip
    Reach for `kmpTargetsInfo` to see *what* the plugin decided; reach for `kmpTargetsDoctor` when a build broke and you want *why*, the consequence, and the fix in one cached run.
