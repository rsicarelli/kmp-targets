plugins {
    // Supports jvm only; registers jvm under KMP_TARGETS=jvm. Depends on :lib through a project
    // dependency — a real cross-project edge that Isolated Projects must wire while keeping each
    // project's configuration isolated.
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
    id("com.ncorti.ktfmt.gradle")
}

ktfmt { kotlinLangStyle() }

tasks.named("check") { dependsOn("ktfmtCheck") }

kmpTargets { supported { jvm } }

kotlin { sourceSets { commonMain.dependencies { implementation(project(":lib")) } } }
