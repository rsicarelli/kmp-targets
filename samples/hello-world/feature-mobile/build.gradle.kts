plugins {
    // Supports Android + all iOS (the `mobile` preset). With
    // kmptargets.targets=jvm,iosArm64,iosSimulatorArm64
    // the jvm is dropped (not supported here) and the two selected iOS leaves register and share a
    // single `iosMain` — with NO `appleMain`/`nativeMain`. Android isn't selected, so no AGP. The
    // starkest collapse: KGP's default would add 3 redundant intermediates here; the template adds
    // one.
    //
    // This module also demos the android-without-AGP advisory (#51): it supports `mobile` (which
    // includes androidTarget) but applies no Android Gradle plugin, so selecting android skips the
    // leaf and warns instead of dying on KGP's raw missing-AGP error. See it fire:
    //   ./gradlew :feature-mobile:kmpTargetsInfo -Pkmptargets.targets=android
    // (add -Pkmptargets.strict=true to make it fail with the same text)
    kotlin("multiplatform")
    alias(libs.plugins.kmpTargets)
}

kmpTargets { supports { mobile } }
