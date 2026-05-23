plugins {
    // Supports every target. With KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64 this registers those
    // three; the minimal template shares the two iOS leaves under a single `iosMain` (no redundant
    // `appleMain`/`nativeMain`). Compare with :legacy-default, which opts out and keeps both.
    id("com.rsicarelli.kmptargets.library") version "0.1.0-SNAPSHOT"
}
