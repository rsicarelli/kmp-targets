# ktorfit × kmp-targets — Android-only experiment (findings)

> Findings from the experiment testing the hypothesis: **"`kmp-targets` does not work with ktorfit
> when a module selects Android only (no iOS)."**

---

## 1. Question and context

[ktorfit](https://foso.github.io/Ktorfit/) is a Retrofit-style HTTP client library for Kotlin
Multiplatform (KMP). It generates the implementation code for your API interfaces via **KSP** (Kotlin
Symbol Processing) — e.g. the `Ktorfit.createMyApi()` extension.

`kmp-targets` registers KMP targets **dynamically**, based on the selection
(`kmpTargets { supports { … } } ∩ kmptargets.targets`). The question: when a module registers **only
the Android target**, does ktorfit still work?

## 2. Short answer

**Yes — it works, with one caveat about *where* the ktorfit code lives.**

ktorfit's KSP processor runs correctly for an Android-only selection and generates `createFakeApi()`.
The only catch is the shape of a single-target KMP module: it has **no** `commonMain` metadata
compilation, so the ktorfit interface + call site must live in the target's own source set
(**`androidMain`**), **not** `commonMain`. With the code in `androidMain`, the Android-only build
**passes**.

> This is **not** a `kmp-targets` bug — the plugin faithfully registers exactly the one target
> requested. The constraint is entirely about how ktorfit/KSP handles a single-target KMP graph.

## 3. What the experiment contains

A standalone build (consumes the plugin from `mavenLocal()`, like `samples/hello-world`) with the
**same** fake ktorfit API in two modules:

| Module | `kmpTargets { supports { … } }` | Code location | Result |
| --- | --- | --- | --- |
| `:api-android-only` | `androidTarget` | `androidMain` | **BUILD SUCCESSFUL** |
| `:api-multiplatform` (control) | `androidTarget + jvm` | `commonMain` | **BUILD SUCCESSFUL** |

The "API" is deliberately fake (`FakeApi.ping()`); its only job is to **force** ktorfit's KSP
processor to generate the `createFakeApi()` extension and prove the call site can resolve it.

## 4. What works and what doesn't

ktorfit generates the `create<Api>()` extension via KSP. *Where* that generated symbol lands — and
which compilation can see it — is the whole story:

- ✅ **Android-only, API in `androidMain`:** `kspDebugKotlinAndroid` emits `createFakeApi` into
  `build/generated/ksp/android/androidDebug/…`, a directory the KSP plugin **already** wires into the
  Android compilation. Interface + generated extension compile together. **Works.**
- ✅ **Android + JVM (control), API in `commonMain`:** two targets ⇒ KGP creates a `commonMain`
  metadata compilation ⇒ `kspCommonMainKotlinMetadata` runs and emits into
  `build/generated/ksp/metadata/commonMain/kotlin/…`, on `commonMain`'s source path. **Works.**
- ❌ **Android-only, API in `commonMain`:** a single target has **no** `commonMain` metadata
  compilation, so there is **no** `kspCommonMainKotlinMetadata` task and nothing puts `createFakeApi`
  on `commonMain`'s path. `compileKotlinMetadata` fails with `Unresolved reference 'createFakeApi'`.
- ❌ **The "metadata task" workaround for the single-target case.** The commonly cited fix —
  `dependsOn("kspCommonMainKotlinMetadata")` + add the metadata KSP dir to `commonMain` — does **not**
  work for a true single target, because that task does not exist. It fails at configuration with
  `Task with name 'kspCommonMainKotlinMetadata' not found` (exactly
  [issue #593](https://github.com/Foso/Ktorfit/issues/593)).

This matches the known ktorfit issues for narrow/Android-only KMP setups:
[#593](https://github.com/Foso/Ktorfit/issues/593) (`kspCommonMainKotlinMetadata` not found),
[#965](https://github.com/Foso/Ktorfit/issues/965) (generated code missing with only an Android
target), and [#638](https://github.com/Foso/Ktorfit/issues/638) (ktorfit Gradle plugin vs. the
Android KMP library plugin).

## 5. Root cause (in detail)

In a KMP build, `commonMain` code is produced by the **`kspCommonMainKotlinMetadata`** task and
emitted into `build/generated/ksp/metadata/commonMain/kotlin/…`, which is a source root of
`commonMain`.

- **Control (Android + JVM):** `compileCommonMainKotlinMetadata` / `kspCommonMainKotlinMetadata`
  exist → `createFakeApi` lands in `…/ksp/metadata/commonMain/…` → `commonMain` resolves it. ✅
- **Android-only:** with a **single target**, KGP does **not** create the `commonMain` metadata
  compilation, so `kspCommonMainKotlinMetadata` never exists. ktorfit's KSP only emits `createFakeApi`
  into the per-target dir `build/generated/ksp/android/androidDebug/…`, which is **not** on
  `commonMain`'s path → the call site in `commonMain` can't see it → compile fails. ❌

The fix is to move the call site into `androidMain`: there it joins the **same** Android compilation
that already receives the KSP-generated directory.

## 6. How to make ktorfit + KSP work with Android-only

Two clean options — both on the ktorfit/KSP side, **neither** a `kmp-targets` concern:

### Option A (recommended for single target) — keep the API in `androidMain`

In a single-target module there is no truly "common" code, so putting the interface + the
`create<Api>()` call in the target's source set costs nothing and resolves the problem.

```
api-android-only/
└── src/
    └── androidMain/kotlin/com/rsicarelli/ktorfit/sample/FakeApi.kt   ← here, NOT in commonMain
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.kmpTargets)
}

android {
    namespace = "com.rsicarelli.ktorfit.sample.androidonly"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

ktorfit {
    // Kotlin 2.3.x requires pinning the ktorfit compiler plugin explicitly (the old `kotlinVersion`
    // property is deprecated). See the ktorfit compatibility table.
    compilerPluginVersion.set(libs.versions.ktorfitCompilerPlugin.get()) // "2.3.3"
}

kotlin {
    sourceSets {
        // The ktorfit dependency rides on commonMain (androidMain inherits it). The CODE is in androidMain.
        commonMain.dependencies { implementation(libs.ktorfit.lib) }
    }
}

// The crux: ANDROID ONLY. No iOS, no JVM.
kmpTargets { supports { androidTarget } }
```

```kotlin
// src/androidMain/kotlin/.../FakeApi.kt
package com.rsicarelli.ktorfit.sample

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.http.GET

interface FakeApi {
    @GET("ping") suspend fun ping(): String
}

// Calls the ktorfit-GENERATED extension — its resolution is the whole test.
fun fakeApi(): FakeApi =
    Ktorfit.Builder().baseUrl("https://example.com/").build().createFakeApi()
```

### Option B — give the build a second target

Add a second target (e.g. `jvm`) so a `commonMain` metadata compilation — and therefore the
`kspCommonMainKotlinMetadata` task — exists, keeping the API in `commonMain`. That is exactly the
control module (`:api-multiplatform`).

```kotlin
kmpTargets { supports { androidTarget + jvm } }
```

### ⚠️ What NOT to do on a single target

Do not attempt the manual `kspCommonMainKotlinMetadata` wiring:

```kotlin
// Does NOT work with a single target — the referenced task is never created (Foso/Ktorfit#593):
dependencies { add("kspCommonMainMetadata", "de.jensklingenberg.ktorfit:ktorfit-ksp:2.7.1") }
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata") // ← blows up here
}
```

Resulting error:

```
> Task with name 'kspCommonMainKotlinMetadata' not found in project ':api-android-only'.
```

## 7. How KSP behaves in a single-target KMP build (mental model)

- ktorfit's KSP processor **runs** per Android variant: `kspDebugKotlinAndroid`,
  `kspReleaseKotlinAndroid`. Confirm with `./gradlew :api-android-only:tasks --all | grep -i ksp`.
- The generated code (`_FakeApiImpl.kt`, containing `fun Ktorfit.createFakeApi()`) lands in
  `build/generated/ksp/android/androidDebug/kotlin/…`.
- The KSP plugin already adds that directory to the **Android compilation**'s sources. So any code
  that needs to see `createFakeApi()` must be in the **same compilation** — i.e. `androidMain` (or
  `androidDebug`/`androidRelease`), not `commonMain`.
- There is no `kspCommonMainKotlinMetadata` with a single target, because KGP does not create the
  common metadata compilation. That is the root of all the confusion.

### Verified matrix — when does `kspCommonMainKotlinMetadata` exist?

Measured with a real KSP build (a temporary `jvm + js + iosArm64` probe module driven under different
`-Pkmptargets.targets` selections; task existence is decided at configuration, so iOS/JS never
compiled). For `jvm,js` the task was also *run*, generating `createFakeApi` into
`build/generated/ksp/metadata/commonMain/kotlin/`.

| Selection | platform targets | native? | `kspCommonMainKotlinMetadata` |
|---|---|---|---|
| `jvm` | 1 | no | **absent** |
| `iosArm64` | 1 | **yes** | **absent** ← native alone is *not* enough |
| `jvm,js` | 2 | **no** | **present** ← no native, yet it runs and generates |
| `jvm,iosArm64` | 2 | yes | present |
| `android,jvm` | 2 | no | present (confirmed via generated artifact) |
| `android` | 1 | no | absent (single target) |

**Rule:** `kspCommonMainKotlinMetadata` exists **iff ≥2 platform targets share `commonMain`** —
identical to KGP's `compileCommonMainKotlinMetadata`. Native presence is neither necessary nor
sufficient. This **disproves** the repo's prior "commonMain KSP needs a native target" guidance
(`docs/user-guide/recipes.md`); the real trap is a **single target**, native or not.

## 8. Two AGP-9 caveats found along the way

1. **`com.android.library` + KMP is rejected on AGP 9.0+.** `kmp-targets` registers Android via KGP's
   classic `androidTarget()`, which needs the classic library plugin. To keep that path on AGP 9, this
   sample sets `android.builtInKotlin=false` and `android.newDsl=false` (see `gradle.properties`). The
   modern alternative — `com.android.kotlin.multiplatform.library` with `kotlin { androidLibrary { … } }`
   — is **not** what `kmp-targets`' `androidTarget()` drives.
2. Config cache is left **off** here to keep the AGP + KSP + ktorfit signal clean.
3. The daemon **metaspace** needed more headroom (AGP + KSP + KMP in one build exhausted the default
   and the daemon expired mid-build). Set `org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g`.

## 9. Versions used

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

## 10. How to reproduce

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
`api-android-only/src/androidMain/kotlin/.../FakeApi.kt` back to `commonMain` and rebuild —
`compileKotlinMetadata` fails with `Unresolved reference 'createFakeApi'`.

> `local.properties` (the `sdk.dir`) is git-ignored — set `ANDROID_HOME` or create it locally.

## 11. Final verdict

The hypothesis "`kmp-targets` does not work with ktorfit" is **only partially confirmed**, and for a
reason that is **not** about `kmp-targets`:

- `kmp-targets` does exactly what it promises: it registers only the `androidTarget`.
- ktorfit/KSP is what does **not** build a common metadata compilation for a single target — so the
  ktorfit API cannot live in `commonMain` in that scenario.
- **Fix**: put the API in `androidMain` (single target) **or** add a second target. Both are
  ktorfit/KSP configuration choices, and the Android-only build passes.
