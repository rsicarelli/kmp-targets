plugins {
    // Supports every target; intersected with KMP_TARGETS=jvm (see ../gradle.properties) it registers
    // jvm only. Configured under Isolated Projects — proof the convention applies without reaching
    // across projects.
    id("com.rsicarelli.kmptargets.library") version "0.1.0-SNAPSHOT"
}
