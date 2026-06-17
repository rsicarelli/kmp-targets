plugins {
    // Shared KGP classloader (the root build declares KGP `apply false`), plus the sample's own
    // `kmptargets.module` convention from the `build-logic` included build.
    kotlin("multiplatform")
    id("kmptargets.module")
}

// EAGER PROOF. `kmpModule` declares the supported set and wires one task per registered target
// through the plugin's `onRegistered` hook (issue #52) — each carrier arrives with the actual
// Gradle name pre-mangled (`gradleNameCapitalized`), the same shape KSP-style `ksp<Target>`
// configuration wiring needs. With kmptargets.targets=jvm,iosArm64,iosSimulatorArm64 intersected
// against supports { jvm + iosArm64 }, the convention sees exactly [iosArm64, jvm] — at
// configuration time, in the same pass. (Pre-#52 this read `kotlin.targets.names` by hand.)
// FRAMEWORK PROOF (.kt / build-logic). `appleFramework = "EagerShared"` declares the framework from
// the convention via the raw `on: KmpTargetSet` overload (no type-safe receiver needed in
// build-logic), and `framework { … }` configures the real KGP `Framework`. It attaches only to the
// registered Apple leaf (iosArm64), never to jvm.
kmpModule(
    supported = { jvm + iosArm64 },
    appleFramework = "EagerShared",
    framework = { isStatic = false },
) { target ->
    tasks.register("describe${target.gradleNameCapitalized}") {
        val gradleName = target.gradleName
        doLast { logger.lifecycle("eager-wired target: $gradleName") }
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
