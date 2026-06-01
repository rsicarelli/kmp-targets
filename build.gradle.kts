// Root build — intentionally minimal.
// The plugin lives under :gradle-plugin. Future modules (e.g. :cli) will sit
// alongside it. Add shared configuration here only when more than one
// subproject needs it.
//
// ktfmt is applied here so the root scripts (this file, settings.gradle.kts)
// are covered by `task fmt` / `task fmt:check` alongside :gradle-plugin's own
// sources. Kotlin is declared `apply false` only to put KGP on the buildscript
// classpath — ktfmt's plugin class references `KotlinSourceSet` and refuses to
// load without it. Each sample project applies ktfmt itself, since the samples
// are standalone builds.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktfmt)
    base
}

ktfmt { kotlinLangStyle() }

tasks.named("check") { dependsOn("ktfmtCheck") }
