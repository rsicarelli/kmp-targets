// Root of the multi-module sample.
// Each subproject applies KGP + the `com.rsicarelli.kmptargets` base plugin and declares its
// supported set via the `kmpTargets { supported { … } }` DSL. The global KMP_TARGETS
// (gradle.properties) is intersected against each module's supported set.
//
// Kotlin Multiplatform is declared here once with `apply false` so every subproject shares a single
// KGP classloader. Without this, KGP's shared KotlinNativeBundleBuildService conflicts across
// sibling projects loaded by different classloaders.
//
// ktfmt is applied here and propagated to every subproject so the sample's build scripts and any
// Kotlin sources are checked alongside the main repo's by `task fmt` / `task fmt:check`.
//
// Kotlin and ktfmt versions come from the main repo's `gradle/libs.versions.toml` (imported by
// `settings.gradle.kts`) so they stay in lock-step with the plugin under test.
plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get() apply false
    id("com.ncorti.ktfmt.gradle") version libs.versions.ktfmt.get()
    base
}

ktfmt { kotlinLangStyle() }

tasks.named("check") { dependsOn("ktfmtCheck") }

subprojects {
    plugins.apply("com.ncorti.ktfmt.gradle")
    extensions.configure<com.ncorti.ktfmt.gradle.KtfmtExtension> { kotlinLangStyle() }
    tasks.matching { it.name == "check" }.configureEach { dependsOn("ktfmtCheck") }
}
