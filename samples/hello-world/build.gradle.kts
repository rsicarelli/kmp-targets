// Root of the multi-module sample.
// Most subprojects apply a `com.rsicarelli.kmptargets.*` convention plugin to declare their supported
// targets; the global KMP_TARGETS (gradle.properties) is intersected against each.
//
// Kotlin Multiplatform is declared here once with `apply false` so every subproject shares a single
// KGP classloader — both those that apply it via a convention plugin and :plain-kmp, which applies it
// directly. Without this, KGP's shared KotlinNativeBundleBuildService conflicts across sibling
// projects loaded by different classloaders.
plugins { kotlin("multiplatform") version "2.3.21" apply false }
