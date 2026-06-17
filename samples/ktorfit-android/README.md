# ktorfit × kmp-targets — Android-only experiment

**Question:** does [ktorfit](https://foso.github.io/Ktorfit/) work with `kmp-targets` when a module
selects **Android only (no iOS)**?

**Answer: Yes — with one caveat about *where* the ktorfit code lives.** ktorfit's KSP processor runs
correctly for an Android-only `kmp-targets` selection and generates `createFakeApi()`. The only catch
is the shape of a single-target KMP module: it has no `commonMain` metadata compilation, so the
ktorfit interface + call site must live in the target's own source set (`androidMain`), **not**
`commonMain`. Put it in `androidMain` and the Android-only build succeeds.

This is **not** a `kmp-targets` bug — `kmp-targets` faithfully registers exactly the one target asked
for. The constraint is entirely ktorfit/KSP's handling of a single-target KMP graph.

---

## What this sample contains

A standalone build (consumes the plugin from `mavenLocal()`, like `samples/hello-world`) with the
same fake ktorfit API in two modules:

| Module | `kmpTargets { supports { … } }` | Source location | Result |
| --- | --- | --- | --- |
| `:api-android-only` | `androidTarget` | `androidMain` | **BUILD SUCCESSFUL** |
| `:api-multiplatform` (control) | `androidTarget + jvm` | `commonMain` | **BUILD SUCCESSFUL** |

The "API" is deliberately fake (`FakeApi.ping()`); its only job is to force ktorfit's KSP processor
to generate the `createFakeApi()` extension and prove the call site can resolve it.

## What works, and what doesn't

ktorfit generates the `create<Api>()` extension via KSP. *Where* that generated symbol lands — and
which compilation can see it — is the whole story:

- ✅ **Android-only, API in `androidMain`:** `kspDebugKotlinAndroid` emits `createFakeApi` into
  `build/generated/ksp/android/androidDebug/…`, which the KSP plugin already wires into the Android
  compilation. Interface + generated extension compile together. **Works.**
- ✅ **Android + JVM (control), API in `commonMain`:** two targets ⇒ KGP creates a `commonMain`
  metadata compilation ⇒ `kspCommonMainKotlinMetadata` runs and emits into
  `build/generated/ksp/metadata/commonMain/kotlin/…`, on `commonMain`'s source path. **Works.**
- ❌ **Android-only, API in `commonMain`:** a single target has **no** `commonMain` metadata
  compilation, so there is no `kspCommonMainKotlinMetadata` task and nothing puts `createFakeApi` on
  `commonMain`'s path. `compileKotlinMetadata` fails with `Unresolved reference 'createFakeApi'`.
- ❌ **The "metadata task" workaround for the single-target case.** The commonly cited fix —
  `dependsOn("kspCommonMainKotlinMetadata")` + add the metadata KSP dir to `commonMain` — cannot work
  for a true single target, because that task does not exist. It fails at configuration with
  `Task with name 'kspCommonMainKotlinMetadata' not found` (exactly
  [#593](https://github.com/Foso/Ktorfit/issues/593)).

This matches the known ktorfit issues for narrow/Android-only KMP setups:
[#593](https://github.com/Foso/Ktorfit/issues/593) (`kspCommonMainKotlinMetadata` not found),
[#965](https://github.com/Foso/Ktorfit/issues/965) (generated code missing with only an Android
target), and [#638](https://github.com/Foso/Ktorfit/issues/638) (ktorfit Gradle plugin vs. the
Android KMP library plugin).

## If you need Android-only with ktorfit

Two clean options — both ktorfit/KSP-side, neither a `kmp-targets` concern:

1. **Keep the ktorfit interface + `create<Api>()` call site in the target source set (`androidMain`),
   not `commonMain`.** With a single target there is no shared code to speak of, so this costs
   nothing and is what `:api-android-only` does.
2. **Give the build a second target** (e.g. `jvm`) so a `commonMain` metadata compilation — and thus
   `kspCommonMainKotlinMetadata` — exists, and keep the API in `commonMain`. That is the control
   module.

Do **not** reach for the manual `kspCommonMainKotlinMetadata` wiring on a true single-target module:
the task it hooks into is never created.

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

# 3. Android-only → SUCCEEDS (API lives in androidMain)
./gradlew -p samples/ktorfit-android :api-android-only:build

# 4. Control, Android + JVM → SUCCEEDS (API lives in commonMain)
./gradlew -p samples/ktorfit-android :api-multiplatform:build
```

To see the failure for yourself, move
`api-android-only/src/androidMain/kotlin/.../FakeApi.kt` back to `commonMain` and rebuild:
`compileKotlinMetadata` fails with `Unresolved reference 'createFakeApi'`.

> `local.properties` (the `sdk.dir`) is git-ignored — set `ANDROID_HOME` or create it locally.
