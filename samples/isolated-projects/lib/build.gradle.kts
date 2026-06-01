plugins {
    // Supports all targets (default when no `kmpTargets { supported { … } }` block is declared);
    // intersected with KMP_TARGETS=jvm (see ../gradle.properties) it registers jvm only. Configured
    // under Isolated Projects — proof the plugin applies without reaching across projects.
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
    id("com.ncorti.ktfmt.gradle")
}

ktfmt { kotlinLangStyle() }

tasks.named("check") { dependsOn("ktfmtCheck") }
