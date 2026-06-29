# Troubleshooting

Start with [`kmpTargetsInfo`](../user-guide/diagnostics.md#kmptargetsinfo): it prints what resolved, which source won, and what registered, per module. For findings with fixes attached, [`kmpTargetsDoctor`](../user-guide/diagnostics.md#kmptargetsdoctor).

## Resolution & installation

| Symptom | Cause | Fix |
|---|---|---|
| `Could not find com.rsicarelli:kmp-targets-gradle-plugin:0.1.0 | repository list doesn't cover the version's home | releases live on `mavenCentral()`, `-SNAPSHOT`s only on `https://central.sonatype.com/repository/maven-snapshots/` — add the right repo to `pluginManagement` ([Installation](../get-started/index.md)) |
| Snapshot doesn't update | Gradle caches changing modules for 24h | `./gradlew build --refresh-dependencies` |
| `Plugin [id: 'com.rsicarelli.kmptargets'] was not found` | `mavenCentral()` missing from `pluginManagement.repositories` (it's not on the Plugin Portal) | add it ([Installation](../get-started/index.md)) |

## Selection

| Symptom | Cause | Fix |
|---|---|---|
| Build fails at configuration: `Unknown target token '...' — did you mean ...?` | typo in `kmptargets.targets`; the parser is strict | accept the suggestion; vocabulary is in [`kmpTargetsInfo`](../user-guide/diagnostics.md#kmptargetsinfo) or the [reference](../user-guide/targets-reference.md) |
| Build fails: unknown key in `kmp-targets.properties` | the config files accept only known `kmptargets.*` keys | fix the key (the error suggests the nearest match) |
| Selection ignored / surprising value wins | a higher-precedence source is set — often a stale `kmp-targets.local.properties` or an env var | `kmpTargetsInfo` names the winning source; check the [precedence chain](../user-guide/selection-layers.md) |
| A module builds nothing | empty overlap: selection ∩ supported = ∅ | widen the selection or the module's `supports { }`; the [empty-overlap advisory](../user-guide/advisories.md#empty-overlap) names the module |
| Every sync re-configures after changing selection | each distinct value is its own configuration-cache entry — only the first build of a value misses | expected; alternating between known values hits the cache ([the trade-off](../why-kmp-targets.md#the-configuration-cache-trade-off)) |
| Changed selection but the IDE still shows the old targets | reaching for `gradlew stop`? not needed | the config files are tracked cache inputs — edit `kmp-targets.local.properties` and Sync; the edit alone refreshes ([IDE Workflow](../user-guide/ide-workflow.md)) |

## Registration

| Symptom | Cause | Fix |
|---|---|---|
| `androidTarget` missing despite being selected + supported | no Android Gradle plugin applied when `supports { }` ran | apply `com.android.library`/`com.android.application` **before** `supports { }` ([the ordering rule](../user-guide/advisories.md#android-target-without-agp)) |
| `compileCommonMainKotlinMetadata` fails on a module that registered zero targets | the [inert-module trap](../user-guide/advisories.md#inert-modules): KGP materializes the metadata compilation even with no platform targets | gate compilations on `kmpTargets.registered().isEmpty()` ([recipe](../user-guide/recipes.md#gate-compilation-on-inert-modules)) |
| Convention plugin misses targets when iterating `kotlin.targets` | eager snapshot raced `supports { }` | use [`onRegistered`](../user-guide/build-logic.md#onregistered-the-ordering-immune-hook) — it replays and fires deltas |
| `targetName` throws | called after `supports { }`, on a non-jvm leaf, or with a blank name | rename [must precede registration and only applies to `jvm`](../user-guide/selection-dsl.md#renaming-the-jvm-target) |

## Building

| Symptom | Cause | Fix |
|---|---|---|
| Variant-resolution wall of text on an inter-module dependency | dependency's supported set doesn't cover a target the dependent builds | check both modules with `kmpTargetsInfo`; widen the dependency's `supports { }` ([recipe](../user-guide/recipes.md#selection-vs-the-dependency-graph)) |
| Variant-resolution wall of text on a `compileDependencyFiles` config after narrowing to `android`-only | android consumers were resolving against producers' **jvm fallback** variant; a pure-`android` selection dropped it | co-select `jvm` with `android` (`kmptargets.targets=android,jvm`) ([recipe](../user-guide/recipes.md#the-asymmetric-case-android-and-the-jvm-fallback)) |
| Unresolved `expect` after narrowing to one iOS leaf | minimal template collapsed the single-child `iosMain` your sources live in | [no-collapse](../user-guide/hierarchy-template.md#keeping-intermediates) |
| iOS compile/link fails on a Linux/Windows runner | host can't compile the registered Apple target — registration is host-blind | per-host selections ([CI](../user-guide/ci-matrix.md)); the [host advisory](../user-guide/advisories.md#host-compatibility) names the mismatch |
| CI job fails at configuration with an advisory's text | [strict mode](../user-guide/advisories.md#strict-mode) promotes advisories to failures | that's the point — fix the flagged configuration, or scope strict to lanes the host can fully compile |
| `apiCheck`/BCV dumps disappear for some targets | BCV ran in a narrowed lane and saw only registered targets | run BCV tasks from a full-selection lane only ([recipe](../user-guide/recipes.md#binary-compatibility-validator-selection)) |
| KSP-generated symbols missing from commonMain | only one target registered in this selection, so the shared commonMain metadata route (`kspCommonMainKotlinMetadata`) never ran | move the codegen-consuming code to the target source set, or register a second target ([recipe](../user-guide/recipes.md#commonmain-ksp-needs-two-targets)); `kmpTargetsDoctor` flags it as `single-target KSP` |

Still stuck? [Open an issue](https://github.com/rsicarelli/kmp-targets/issues) with the `kmpTargetsInfo` output for the affected module — it carries exactly the state needed to diagnose.
