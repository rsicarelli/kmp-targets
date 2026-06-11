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
        commonMain.dependencies { implementation(libs.ktorfit.lib) }
    }
}

// The crux: ANDROID ONLY. No iOS, no JVM. ktorfit's KSP codegen must run for the Android target alone.
kmpTargets { supports { androidTarget } }
