plugins {
    // Supports all targets via the explicit `supports { all }` below. Intersected with the global
    // kmptargets.targets=jvm (see ../kmp-targets.properties) it registers jvm only. Configured
    // under Isolated
    // Projects — proof the plugin applies without reaching across projects.
    kotlin("multiplatform")
    alias(libs.plugins.kmpTargets)
    id("com.ncorti.ktfmt.gradle")
}

kmpTargets { supports { all } }

ktfmt { kotlinLangStyle() }

tasks.named("check") { dependsOn("ktfmtCheck") }
