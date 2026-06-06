# Why kmp-targets?

## The full-target-build problem

A plain KMP module registers every target its build script declares, every time, for everyone. A shared module with 20+ targets drags all of them through every Gradle sync and every aggregate invocation — compile, link, KSP, publish task graphs for watchOS variants you haven't touched in months, on a laptop that only needed `jvm` and one iOS leaf today.

The waste compounds in two directions:

- **Per-developer**: an Android engineer iterating on `jvm`+`androidTarget` pays configuration and task-graph cost for every Apple and native target in every module, on every sync.
- **Per-CI-host**: a Linux runner *cannot* compile `iosArm64`, yet a full-target build still registers it, configures it, and either fails or wastes time skipping it. macOS runners — billed at roughly 10× the Ubuntu rate on private repos — end up building targets Ubuntu could have handled.

KGP's default hierarchy template adds a second tax: it materializes the *full* intermediate source-set tree for whatever registers, so an iOS-only module still carries `nativeMain` and `appleMain` — each redundant intermediate spawning ~8 wasteful tasks ([the hidden cost of default hierarchy templates](https://dev.to/rsicarelli/the-hidden-cost-of-default-hierarchy-templates-in-kotlin-multiplatform-256a)).

## The thesis: explicit selection

`kmp-targets` separates two facts that full-target builds conflate:

| Fact | Declared by | Mechanism |
|---|---|---|
| What a module **can** build | the module, in git | `kmpTargets { supports { … } }` |
| What you **want** built now | the developer / CI job | the global `kmptargets.targets` property |

The plugin registers `selection ∩ supported` per module. Unselected targets are not "disabled" — they are **never registered with KGP at all**, so their tasks don't exist. The build doesn't skip work; the work isn't there.

Selection is **explicit and deterministic**, not host-magic: selecting `iosArm64` on a Linux box registers `iosArm64` (with an [advisory](user-guide/advisories.md), since the host can't compile it). Registration being host-blind keeps configuration-cache keys, task graphs, and published metadata identical across machines — what differs per host is what you *select*, via the [same vocabulary everywhere](user-guide/ci-matrix.md).

And because the plugin knows the active target set, it applies a [**minimal** hierarchy template](user-guide/hierarchy-template.md) — only the intermediates your registered targets actually need.

## The trade-off, stated plainly

Changing the selection changes what the configuration phase produces. The price is exact: **one configuration-cache miss + one Gradle sync per new `kmptargets.targets` value**. Each distinct value is its own cache entry, and entries coexist — switching *back* to a previous selection is a cache **hit**. Alternating between two working sets re-configures nothing after the first time each was seen.

There is deliberately no file-watch workaround: Gradle has no mechanism to re-run the *configuration* phase on a file change, and the plugin won't fake one. The bounded miss is the price of not registering unused targets — the same property that makes every subsequent sync and build smaller.

!!! tip
    If a selection ever surprises you, [`kmpTargetsInfo`](user-guide/kmp-targets-info.md) prints what resolved, which source won, and what registered — at configuration-cache-hit speed.

## What it is not

- **Not a convention plugin.** One extension, one DSL, no bundled per-shape plugin ids. Teams that want `apply by id, no body` ergonomics [build their own conventions](user-guide/build-logic.md) on top.
- **Not a host auto-gater.** The plugin never decides "this host can't build that, so drop it" — you select, it registers, advisories tell you when a combination can't work [and strict mode makes that fatal in CI](user-guide/advisories.md).
- **Not a fork of the target model.** Everything registers through plain KGP APIs; modules that don't apply the plugin are untouched (see the [`plain-kmp` sample](samples/index.md)).
