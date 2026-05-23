plugins {
    kotlin("multiplatform") version "2.3.21"
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

kotlin {
    sourceSets.commonMain.dependencies {
        // empty on purpose — the sample exercises plugin wiring, not third-party deps
    }
}
