# FAQ

Short answers; each links to the page with the full story.

## Selection model

**Why is the selection global instead of per-module?**
"What to build now" is a property of the invocation, not of any module. See [Design](../why-kmp-targets.md#selection-model).

**What happens to targets I don't select?**
They are never registered with KGP. Their compile/link/KSP/publish tasks don't exist — nothing is "disabled" or skipped.

**Does selecting a target a host can't compile drop it?**
No — registration is [host-blind](../why-kmp-targets.md#selection-model). It registers, an [advisory](../user-guide/advisories.md#host-compatibility) names the mismatch. Vary by host via [per-host selections](../user-guide/ci-matrix.md).

**Why did my selection change trigger a full re-configuration?**
Each distinct `kmptargets.targets` value is its own configuration-cache entry; entries coexist, so switching back is a cache hit. See [the trade-off](../why-kmp-targets.md#the-configuration-cache-trade-off).

**Is there a "build nothing" selection?**
Yes — narrow to an empty set, e.g. `kmptargets.targets=jvm,-jvm`. It's honored deterministically; beware that [strict mode](../user-guide/advisories.md#strict-mode) then fails every module declaring `supports { }` (the inert-module advisory, by design).

## DSL

**A module with `supports { }` never registered anything — why?**
Run [`kmpTargetsInfo`](../user-guide/diagnostics.md#kmptargetsinfo): either the global selection is disjoint from the supported set (empty-overlap), or the only overlap was an `androidTarget` skipped by the [AGP guard](../user-guide/advisories.md#android-target-without-agp).

**Can I call `supports { }` twice?**
Yes — calls **union**. Registration is one-way: an already-registered target can't be retracted.

**Why does `android` need AGP applied before `supports { }`?**
`kotlin.androidTarget()` is a hard KGP failure without an Android Gradle plugin. The plugin skips the leaf with an advisory instead of crashing — [the ordering rule](../user-guide/advisories.md#android-target-without-agp) is the fix.

**Can I rename targets?**
Only the `jvm` leaf, via [`targetName(jvm, "desktop")`](../user-guide/selection-dsl.md#renaming-the-jvm-target) — Android's name is fixed by AGP and native/web names are derived by KGP.

**Why doesn't `kmptargets.targets=ios` work?**
Bare Apple sub-family names are ambiguous (device leaf? all leaves?) and rejected with a hint. Use a leaf (`iosArm64`) or the `appleMobile` preset.

## Ecosystem

**Does it work with the configuration cache and Isolated Projects?**
Yes — both are exercised in CI (the repo runs Gradle 9.5 with the configuration cache on; an [Isolated Projects sample](../samples/index.md#isolated-projects-the-compatibility-gate) carries a regression gate).

**Does it replace my convention plugins?**
No — it's the primitive under them. The plugin ships one extension and zero preset plugin ids; [your conventions](../user-guide/build-logic.md) wrap it in your team's vocabulary.

**Does it touch modules that don't apply it?**
No. The [`plain-kmp` sample](../samples/index.md) exists to prove non-interference.

**Where are the binaries?**
Maven Central (`com.rsicarelli:kmp-targets-gradle-plugin`), snapshots on the Central Portal snapshots repo — see [Installation](../get-started/index.md). There is no Gradle Plugin Portal listing.
