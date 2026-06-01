// Root of the multi-module sample.
// Each subproject applies KGP + the `com.rsicarelli.kmptargets` base plugin and declares its
// supported set via the `kmpTargets { supported { … } }` DSL. The global KMP_TARGETS
// (gradle.properties) is intersected against each module's supported set.
//
// Kotlin Multiplatform is declared here once with `apply false` so every subproject shares a single
// KGP classloader. Without this, KGP's shared KotlinNativeBundleBuildService conflicts across
// sibling projects loaded by different classloaders.
plugins { kotlin("multiplatform") version "2.3.21" apply false }
