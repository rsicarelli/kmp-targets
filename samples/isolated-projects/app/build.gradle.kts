plugins {
    // Supports jvm only; registers jvm under kmptargets.targets=jvm. Depends on :lib through a
    // project
    // dependency — a real cross-project edge that Isolated Projects must wire while keeping each
    // project's configuration isolated.
    kotlin("multiplatform")
    alias(libs.plugins.kmpTargets)
    id("com.ncorti.ktfmt.gradle")
}

ktfmt { kotlinLangStyle() }

tasks.named("check") { dependsOn("ktfmtCheck") }

kmpTargets { supports { jvm } }

kotlin { sourceSets { commonMain.dependencies { implementation(project(":lib")) } } }
