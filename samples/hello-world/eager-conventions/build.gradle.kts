plugins {
    // Shared KGP classloader (the root build declares KGP `apply false`), plus the sample's own
    // `kmptargets.module` convention from the `build-logic` included build.
    kotlin("multiplatform")
    id("kmptargets.module")
}

// EAGER PROOF. `kmpModule` declares the supported set and then reads `kotlin.targets`
// synchronously,
// wiring one task per registered target. With KMP_TARGETS=jvm,iosArm64,iosSimulatorArm64
// intersected
// against supports { jvm + iosArm64 }, the convention sees exactly [iosArm64, jvm] — at
// configuration
// time, in the same pass. Under the old after-evaluate model this read would have been empty.
kmpModule(supported = { jvm + iosArm64 }) { targets ->
    targets.forEach { target ->
        tasks.register("describe${target.replaceFirstChar(Char::uppercase)}") {
            doLast { logger.lifecycle("eager-wired target: $target") }
        }
    }
}

// Regression gate, wired into `check` and run by `task ci`. Captures only a List<String> at
// configuration time (config-cache safe), and fails if eager registration ever regresses to a
// deferred pass — in which case this read would see no targets.
val observedTargets = kotlin.targets.names.filter { it != "metadata" }.sorted()

tasks.register("verifyEagerTargets") {
    val observed = observedTargets
    val expected = listOf("iosArm64", "jvm")
    doLast {
        check(observed == expected) {
            "eager registration regressed: expected $expected at configuration time, saw $observed"
        }
    }
}

tasks.named("check") { dependsOn("verifyEagerTargets") }
