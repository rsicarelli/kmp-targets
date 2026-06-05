plugins {
    // Supports all targets via the explicit `supports { all }` below. Intersected with the global
    // kmptargets.targets=jvm (see ../kmp-targets.properties) it registers jvm only. Configured
    // under Isolated
    // Projects — proof the plugin applies without reaching across projects.
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
    id("com.ncorti.ktfmt.gradle")
}

kmpTargets { supports { all } }

ktfmt { kotlinLangStyle() }

tasks.named("check") { dependsOn("ktfmtCheck") }
