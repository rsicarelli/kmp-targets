plugins {
    // Supports jvm only; registers jvm under KMP_TARGETS=jvm. Depends on :lib through a project
    // dependency — a real cross-project edge that Isolated Projects must wire while keeping each
    // project's configuration isolated.
    id("com.rsicarelli.kmptargets.jvm") version "0.1.0-SNAPSHOT"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":lib"))
        }
    }
}
