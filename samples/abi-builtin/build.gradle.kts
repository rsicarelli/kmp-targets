import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// Sample: kmp-targets × Kotlin's built-in ABI validation (#81).
//
// Same shape as the abi-bcv sample, but the ABI tool is the one that ships inside the Kotlin Gradle
// plugin (no third-party plugin): `kotlin { abiValidation { } }`, with updateKotlinAbi /
// checkKotlinAbi tasks. The committed dumps live under api/, and the selection × ABI blind spot —
// plus
// the plugin's task-name hook that warns under a narrowed lane — is identical. See the README.
plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    alias(libs.plugins.kmpTargets)
    id("com.ncorti.ktfmt.gradle") version libs.versions.ktfmt.get()
}

// Targets are explicit; the committed dumps under api/ are generated under this full selection.
kmpTargets { supports { jvm + linuxX64 + js } }

// Built-in ABI validation is experimental (KGP 2.3.21). KLIB validation is opt-in, exactly as in
// kotlinx BCV — without it the native/js targets get no merged dump and the blind spot is
// invisible.
@OptIn(ExperimentalAbiValidation::class)
kotlin {
    abiValidation {
        enabled.set(true)
        klib.enabled.set(true)
    }
}

ktfmt { kotlinLangStyle() }
