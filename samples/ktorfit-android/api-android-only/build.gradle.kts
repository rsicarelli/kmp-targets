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
    compilerPluginVersion.set(libs.versions.ktorfitCompilerPlugin.get())
}

kotlin {
    sourceSets {
        // The ktorfit dependency rides on commonMain (androidMain inherits it). The source itself
        // lives in androidMain — see below.
        commonMain.dependencies { implementation(libs.ktorfit.lib) }
    }
}

// The crux: ANDROID ONLY. No iOS, no JVM. ktorfit's KSP codegen must run for the Android target alone.
kmpTargets { supports { androidTarget } }

// --- The single-target fix (Option 2 from the README) ---------------------------------------------
// ktorfit's KSP processor DOES generate `createFakeApi()` for the lone Android target, into
// build/generated/ksp/android/androidDebug/… — and the KSP plugin already wires that dir into the
// Android compilation. The original failure was only that the call site sat in commonMain, which a
// single-target build compiles via a separate `compileKotlinMetadata` pass that never sees the
// Android-generated code (and there is no `kspCommonMainKotlinMetadata` to feed it).
//
// With exactly one target there is no reason for the code to be "common": keeping FakeApi.kt in
// androidMain puts the interface and the generated extension in the same compilation, and it
// resolves. No manual task wiring required — see src/androidMain.
