# Design

The decisions behind the plugin and their trade-offs. The rest of the docs is reference; the "why" lives here.

## The problem

A plain KMP module registers every declared target, every time, for everyone. A module with 20+ targets drags all of them through every sync and every aggregate invocation: compile, link, KSP, and publish task graphs for targets the current machine doesn't need.

The waste has two sides:

- **Per developer**: someone iterating on `jvm`+`androidTarget` pays configuration and task-graph cost for every Apple and native target in every module, on every sync.
- **Per CI host**: a Linux runner cannot compile `iosArm64`, yet a full-target build still registers and configures it. macOS runners bill at roughly 10× the Ubuntu per-minute rate on private GitHub repos, so every target built there that Ubuntu could host is paid at the premium rate. The [CI page](user-guide/ci-matrix.md) shows the per-host selection pattern this enables.

## Selection model

- **Global, not per-module.** "What to build now" is a property of the invocation, not of any module. Modules declare what they *can* build (`supports { }`); one switch narrows the whole build. Per-module selection would recreate the drift the plugin removes.
- **`selection ∩ supported`.** The plugin registers the intersection per module. Unselected targets are not "disabled" — they are never registered with KGP, so their tasks don't exist.
- **Explicit by default.** A module that never calls `supports { }` registers nothing, exactly like plain KGP without target calls. There is no implicit "all targets" baseline.
- **Host-blind registration.** Selecting `iosArm64` on Linux registers it (with an [advisory](user-guide/advisories.md#host-compatibility)). The plugin never decides "this host can't build that, so drop it". Host-blindness keeps configuration-cache keys, task graphs, and published metadata identical across machines; what varies per host is the *selection*, through the same vocabulary everywhere.
- **Strict parser.** Unknown selection tokens fail the build at configuration time with a "did you mean …?" suggestion. The alternative — silently dropping a misspelled target in CI — produces green builds that didn't build the target.
- **Dedicated properties files outrank `gradle.properties`.** Once a team adopts `kmp-targets.properties`, a stale `kmptargets.targets` key left behind in `gradle.properties` cannot silently override it.

## The configuration-cache trade-off

Changing the selection changes what the configuration phase produces. The price: one configuration-cache miss plus one sync per *new* `kmptargets.targets` value. Each distinct value is its own cache entry and entries coexist, so switching back to a previous selection is a cache hit.

There is no file-watch workaround: Gradle has no mechanism to re-run the configuration phase on a file change, and the plugin won't fake one. The bounded miss is the price of not registering unused targets — the same property that makes every subsequent sync and build smaller.

## Hierarchy

KGP's default hierarchy template materializes the full intermediate source-set tree for whatever registers: an iOS-only module still carries `nativeMain` and `appleMain`, and each redundant intermediate adds roughly 8 Gradle tasks. Across many modules this dominates sync time — measured in [the hidden cost of default hierarchy templates](https://dev.to/rsicarelli/the-hidden-cost-of-default-hierarchy-templates-in-kotlin-multiplatform-256a).

Because the plugin knows each module's registered set, it applies a [minimal template](user-guide/hierarchy-template.md) instead, collapsing every single-child intermediate. The collapse is right for new code; codebases with `actual` implementations living in intermediate source dirs (`src/iosMain`) opt out per group with [no-collapse](user-guide/hierarchy-template.md#keeping-intermediates).

## Diagnostics philosophy

- **Advisories are signal-only.** Seven of the eight never change what registers. The exception is [android-without-AGP](user-guide/advisories.md#android-target-without-agp), which skips the leaf because the alternative is KGP's raw `AndroidGradlePluginIsMissing` crash with no module-level guidance — during build-logic migrations that crash reads as "kmp-targets dropped my target".
- **Strict mode changes severity, never policy.** `kmptargets.strict=true` turns the same advisories with the same text into failures. It never changes which configurations are flagged or what registers.
- **Doctor renders, it does not own predicates.** Every [`kmpTargetsDoctor`](user-guide/diagnostics.md#kmptargetsdoctor) finding keys off the same decision its advisory used, so the report cannot disagree with what happened.

## Compatibility floors

The published jar targets the oldest supported consumer, not the toolchain the repo builds with. There are no Gradle toolchains ([why toolchains are rarely a good idea](https://jakewharton.com/gradle-toolchains-are-rarely-a-good-idea/)); the build pins the emitted output instead:

- Kotlin `languageVersion`/`apiVersion` 2.0 — metadata the Gradle 8.x embedded compiler can read
- `jvmTarget` 17 + `-Xjdk-release=17` / `--release 17` — bytecode JDK 17 daemons can load, with accidental-new-API protection
- a `kotlin-stdlib` 2.0.21 POM dependency — floor consumers never pull 2.3-metadata stdlib jars onto their `kotlin-dsl` compile classpath

A `verifyCompatFloors` task inspects the built jar (bytecode versions, metadata versions, resolved stdlib) on every `check`, so the floors cannot silently regress.

Three related decisions:

- **Track KGP's target model, deliberately.** The leaf vocabulary covers every target KGP 2.3.21 ships; the deprecated set is pinned against Kotlin's [target-support page](https://kotlinlang.org/docs/native-target-support.html) and updated with Kotlin upgrades, never inferred at runtime.
- **Honor explicit selection everywhere.** No host-specific filtering, no version-specific surprises: the same selection registers the same set on every machine that meets the floors.
- **The samples are the compat suite.** A 3-OS CI matrix builds the multi-module sample per host on every change, and an Isolated-Projects sample gates that mode.

## What it is not

- **Not a convention plugin.** One extension, one DSL, no bundled per-shape plugin ids. Teams that want `apply by id, no body` ergonomics [build their own conventions](user-guide/build-logic.md) on top.
- **Not a host auto-gater.** You select, it registers; [advisories](user-guide/advisories.md) name combinations that can't work, and strict mode makes them fatal in CI.
- **Not a fork of the target model.** Everything registers through plain KGP APIs; modules that don't apply the plugin are untouched (see the [`plain-kmp` sample](samples/index.md)).
