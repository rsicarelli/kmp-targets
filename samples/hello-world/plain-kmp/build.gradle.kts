plugins {
    // No kmp-targets here — a plain Kotlin Multiplatform module. Proves non-interference: the
    // plugin only ever touches modules that apply it, so this module keeps KGP's default hierarchy
    // untouched (its two iOS leaves get the full `iosMain` + `appleMain` + `nativeMain` chain).
    kotlin("multiplatform")
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
}
