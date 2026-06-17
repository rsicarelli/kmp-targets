// Sample: kmp-targets × the opt-in Xcode-environment selection source (#109).
//
// A standalone, single-module library that ships an iOS/macOS framework. It commits
// `kmptargets.xcodeEnv=true`, so when Xcode invokes Gradle for embedAndSign…AppleFrameworkForXcode
// its own SDK_NAME/ARCHS/CONFIGURATION env vars drive selection — the consumer's Xcode build phase
// needs NO `-Pkmptargets.targets=…`. Outside Xcode (no env), selection is the committed fallback in
// kmp-targets.properties. The macOS CI lane (sample-matrix.yml) exercises the real path: it sets
// the
// Xcode env, asserts the narrowing via `verifyXcodeEnvNarrowed`, and links the framework.
plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    alias(libs.plugins.kmpTargets)
    id("com.ncorti.ktfmt.gradle") version libs.versions.ktfmt.get()
}

ktfmt { kotlinLangStyle() }

kmpTargets {
    // Supported = every Apple leaf ∪ jvm. The active selection (committed fallback, or the Xcode
    // env
    // when present) is intersected with this, so an Xcode simulator build registers
    // iosSimulatorArm64
    // alone while a plain build registers the committed jvm + iOS set.
    supports { apple + jvm }

    // Declare the framework once; the plugin attaches it to every registered Apple leaf. The
    // configure block is the real KGP `Framework`. With no `buildTypes` arg it defaults to
    // DEBUG+RELEASE; Xcode's CONFIGURATION (via #108's intersection) narrows it at build time.
    appleFramework("XcodeEnvShared") { isStatic = false }
}

// CI assertion (macOS lane, not wired into `check`): when Xcode's simulator env is present the
// selection must have narrowed to exactly iosSimulatorArm64; otherwise it just reports the
// committed
// fallback. Config-cache safe — a List<String> and the SDK_NAME string are captured at
// configuration
// time; the action references only those primitives (no Project capture).
val observedTargets = kotlin.targets.names.filter { it != "metadata" }.sorted()
val sdkName = providers.environmentVariable("SDK_NAME")

tasks.register("verifyXcodeEnvNarrowed") {
    val observed = observedTargets
    val sdk = sdkName.orNull
    doLast {
        if (sdk != null && sdk.startsWith("iphonesimulator")) {
            check(observed == listOf("iosSimulatorArm64")) {
                "Xcode-env narrowing regressed: SDK_NAME=$sdk must narrow selection to " +
                    "[iosSimulatorArm64], but registered $observed"
            }
            logger.lifecycle("Xcode-env narrowed selection to $observed (SDK_NAME=$sdk)")
        } else {
            logger.lifecycle(
                "No Xcode simulator env (SDK_NAME=$sdk); selection is the committed fallback: $observed"
            )
        }
    }
}
