plugins {
    // Supports Android + all iOS (the `mobile` preset). With
    // kmptargets.targets=jvm,iosArm64,iosSimulatorArm64
    // the jvm is dropped (not supported here) and the two selected iOS leaves register and share a
    // single `iosMain` — with NO `appleMain`/`nativeMain`. Android isn't selected, so no AGP. The
    // starkest collapse: KGP's default would add 3 redundant intermediates here; the template adds
    // one.
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

kmpTargets { supports { mobile } }
