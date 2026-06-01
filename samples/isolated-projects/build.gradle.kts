// Root of the isolated-projects sample.
// Kotlin Multiplatform is declared once with `apply false` so every subproject shares a single KGP
// classloader (mirrors samples/hello-world). Each subproject applies KGP + the
// `com.rsicarelli.kmptargets` base plugin and declares its supported set via the
// `kmpTargets { supported { … } }` DSL — all under Isolated Projects (see gradle.properties).
//
// ktfmt is applied here so the root scripts (this file + settings.gradle.kts) get formatted; each
// subproject also applies it directly. Isolated Projects forbids `subprojects { … }` cross-project
// configuration, so per-subproject apply is the only IP-compatible pattern. Versions come from the
// main repo's `libs.versions.toml` (imported by `settings.gradle.kts`).
plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get() apply false
    id("com.ncorti.ktfmt.gradle") version libs.versions.ktfmt.get()
    base
}

ktfmt { kotlinLangStyle() }

tasks.named("check") { dependsOn("ktfmtCheck") }
