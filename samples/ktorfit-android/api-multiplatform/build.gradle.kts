plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.kmpTargets)
}

android {
    namespace = "com.rsicarelli.ktorfit.sample.multiplatform"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

ktorfit {
    compilerPluginVersion.set(libs.versions.ktorfitCompilerPlugin.get())
}

kotlin {
    sourceSets {
        commonMain.dependencies { implementation(libs.ktorfit.lib) }
    }
}

// CONTROL: same ktorfit API, but TWO targets (Android + JVM). If this builds while api-android-only
// fails, the breakage is specific to the single-target/Android-only selection — confirming the
// hypothesis is about that selection, not ktorfit itself.
kmpTargets { supports { androidTarget + jvm } }
