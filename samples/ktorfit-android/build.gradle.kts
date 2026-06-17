// KGP (and friends) are declared here with `apply false` so a single classloader is shared across the
// sibling modules — the same pattern hello-world uses to avoid cross-module KGP service conflicts.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktorfit) apply false
    alias(libs.plugins.kmpTargets) apply false
}
