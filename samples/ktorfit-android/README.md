# ktorfit × kmp-targets — Android-only experiment

**Question:** does [ktorfit](https://foso.github.io/Ktorfit/) work with `kmp-targets` when a module
selects **Android only (no iOS)**?

**Answer: No — Android-only fails; the same API across Android + JVM works.** The break is not
ktorfit being incompatible with `kmp-targets` in general; it is specific to a **single-target**
selection.

---

## What this sample contains

A standalone build (consumes the plugin from `mavenLocal()`, like `samples/hello-world`) with the
same fake ktorfit API in two modules:

| Module | `kmpTargets { supports { … } }` | Result |
| --- | --- | --- |
| `:api-android-only` | `androidTarget` | **BUILD FAILED** — `Unresolved reference 'createFakeApi'` |
| `:api-multiplatform` (control) | `androidTarget + jvm` | **BUILD SUCCESSFUL** |

The "API" is deliberately fake (`FakeApi.ping()`); its only job is to force ktorfit's KSP processor
to generate the `createFakeApi()` extension. Whether that generated symbol is resolvable from
`commonMain` *is* the test.

## Root cause

ktorfit generates the `create<Api>()` extension via KSP. In a Kotlin Multiplatform build the common
code is produced by the **`kspCommonMainKotlinMetadata`** task and emitted to
`build/generated/ksp/metadata/commonMain/kotlin/…`, which is a source root of `commonMain`.

- **Control (Android + JVM):** `compileCommonMainKotlinMetadata` / `kspCommonMainKotlinMetadata`
  exist → `createFakeApi` lands in `…/ksp/metadata/commonMain/…` → `commonMain` resolves it. ✅
- **Android-only:** with a **single target**, KGP does **not** create a `commonMain` metadata
  compilation, so `kspCommonMainKotlinMetadata` never exists. ktorfit's KSP only emits
  `createFakeApi` into the per-target dir `build/generated/ksp/android/androidDebug/…`, which is
  **not** on `commonMain`'s source path → the `commonMain` call site can't see it → compile fails. ❌

This matches the known ktorfit issues for narrow/Android-only KMP setups:
[#593](https://github.com/Foso/Ktorfit/issues/593) (`kspCommonMainKotlinMetadata` not found),
[#965](https://github.com/Foso/Ktorfit/issues/965) (generated code missing with only an Android
target), and [#638](https://github.com/Foso/Ktorfit/issues/638) (ktorfit Gradle plugin vs. the
Android KMP library plugin).

It is **not** a `kmp-targets` bug: `kmp-targets` faithfully registers exactly the one target asked
for. The single-target shape it produces is simply one ktorfit's KSP wiring does not handle.

## Two AGP-9 caveats found along the way

1. **`com.android.library` + KMP is rejected on AGP 9.0+.** `kmp-targets` registers Android via
   KGP's classic `androidTarget()`, which needs the classic library plugin. To keep that path on
   AGP 9, this sample sets `android.builtInKotlin=false` and `android.newDsl=false` (see
   `gradle.properties`). The modern alternative — `com.android.kotlin.multiplatform.library` with
   `kotlin { androidLibrary { … } }` — is **not** what `kmp-targets`' `androidTarget()` drives.
2. Config cache is left **off** here to keep the AGP + KSP + ktorfit signal clean.

## Versions

| Tool | Version |
| --- | --- |
| Kotlin | 2.3.21 |
| AGP | 9.2.1 |
| KSP | 2.3.9 |
| ktorfit (lib + plugin) | 2.7.1 |
| ktorfit `compilerPluginVersion` | 2.3.3 (required for Kotlin 2.3.x) |
| Gradle | 9.5.1 (repo wrapper) |
| kmp-targets | 0.1.0-SNAPSHOT (mavenLocal) |

ktorfit 2.7.1 needs the compiler plugin pinned for Kotlin 2.3.x — note the
`ktorfit { compilerPluginVersion.set("2.3.3") }` block in each module's `build.gradle.kts`.

## Reproduce

```bash
# 1. Publish the plugin under test to ~/.m2
task publish-local           # or: ./gradlew publishToMavenLocal

# 2. Point at an Android SDK (compileSdk 36) — this sample's local.properties holds sdk.dir
export ANDROID_HOME=/path/to/android-sdk

# 3. Android-only → FAILS (Unresolved reference 'createFakeApi')
./gradlew -p samples/ktorfit-android :api-android-only:build

# 4. Control, Android + JVM → SUCCEEDS
./gradlew -p samples/ktorfit-android :api-multiplatform:build
```

> `local.properties` (the `sdk.dir`) is git-ignored — set `ANDROID_HOME` or create it locally.

## If you actually need Android-only with ktorfit

Give the KMP build a second target (e.g. `jvm`) so a `commonMain` metadata compilation exists, or
apply the manual ktorfit single-target workaround (wire `kspCommonMainMetadata` and add
`build/generated/ksp/metadata/commonMain/kotlin` to `commonMain`). Neither is a `kmp-targets`
concern — both are about ktorfit's KSP wiring needing the metadata compilation that a single target
doesn't produce.
