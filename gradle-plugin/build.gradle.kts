plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.mavenPublish)
    `java-gradle-plugin`
}

// No toolchain on purpose: build with the locally installed (mise-pinned) JDK, target the oldest
// supported consumer. Floors (Java 17 bytecode, Kotlin 2.0 metadata) live in buildSrc's
// JvmCompatibility.kt — consumers compile their build-logic with Gradle's embedded Kotlin and run
// daemons on JDK 17, so emitting current-toolchain levels would make the jar unconsumable there.
configureJvmCompatibility()

// Inspects the built jar (bytecode + metadata floors, resolved stdlib) and fails `check` on any
// regression — the floors above are only promises until the artifact proves them.
registerCompatFloorsVerification()

// The published stdlib dependency must sit on the same floor: without this, KGP writes the
// toolchain's stdlib (2.3.x) into the POM and floor consumers pull 2.3-metadata jars onto their
// kotlin-dsl compile classpath. Lives here (not JvmCompatibility.kt) because the `kotlin {}` DSL
// type is only on this script's classpath via the applied plugin.
kotlin { coreLibrariesVersion = KOTLIN_FLOOR_STDLIB }

// Publish a -sources.jar alongside the plugin so IntelliJ can render the DSL's KDoc on hover.
// `java-gradle-plugin` builds the `pluginMaven` publication from `components["java"]`; attaching
// the sources artifact to that component is all the wiring maven-publish needs.
java { withSourcesJar() }

ktfmt { kotlinLangStyle() }

val testKitPluginClasspath: Configuration by configurations.creating

tasks.pluginUnderTestMetadata { pluginClasspath.from(testKitPluginClasspath) }

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.gradlePlugin)
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))

    // KGP is compileOnly above (consumers bring their own), but TestKit child builds resolve the
    // plugin classpath from the plugin-under-test metadata — without KGP there, Gradle cannot even
    // decorate KmpTargetsPlugin. Funnel it in through a dedicated resolvable configuration.
    testKitPluginClasspath(libs.kotlin.gradlePlugin)
    // KSP joins the same classpath so CommonMainKspRouteFunctionalTest can apply a real KSP and pin
    // that kspCommonMainKotlinMetadata follows the ≥2-platform-targets rule (#73).
    testKitPluginClasspath(libs.ksp.gradlePlugin)
}

gradlePlugin {
    website.set("https://github.com/rsicarelli/kmp-targets")
    vcsUrl.set("https://github.com/rsicarelli/kmp-targets.git")
    plugins {
        create("kmpTargets") {
            id = "com.rsicarelli.kmptargets"
            implementationClass = "com.rsicarelli.kmptargets.KmpTargetsPlugin"
            displayName = "KMP Targets"
            description =
                "Dynamically select which Kotlin Multiplatform targets to build via the kmptargets.targets Gradle property."
            tags.set(listOf("kotlin", "kmp", "multiplatform", "build", "ios", "android"))
        }
    }
}

// Maven Central publishing (Central Portal via vanniktech maven-publish). The plugin publishes the
// artifact under the coordinates below plus the `com.rsicarelli.kmptargets.gradle.plugin` marker —
// vanniktech leaves marker publications' coordinates untouched, which is exactly what Gradle's
// plugin resolution requires.
//
// RELEASE_MODE=true  → automatic release to Maven Central (release/hotfix workflows)
// RELEASE_MODE=false → staged/SNAPSHOT publishing only (continuous-deploy workflow)
val isReleaseMode = findProperty("RELEASE_MODE")?.toString()?.toBoolean() ?: false
val isSnapshot = version.toString().endsWith("-SNAPSHOT")
val skipSigning = findProperty("SKIP_SIGNING")?.toString()?.toBoolean() ?: false
val isCI = System.getenv("IS_CI")?.toBoolean() ?: false

mavenPublishing {
    publishToMavenCentral(automaticRelease = isReleaseMode)

    // Signing logic:
    // - If IS_CI is absent (false) → skip signing (local development)
    // - If SKIP_SIGNING flag is set → skip signing
    // - If IS_CI is true AND not skipped AND not snapshot → sign
    // This avoids requiring GPG credentials for local publishToMavenLocal.
    if (isCI && !skipSigning && !isSnapshot) {
        signAllPublications()
    }

    // POM metadata (name, description, url, licenses, developers, scm) comes from the POM_* keys
    // in gradle.properties — the vanniktech plugin maps them automatically. Declaring them here
    // too would duplicate the <license>/<developer> list entries in the published POM.
    coordinates(
        groupId = group.toString(),
        artifactId = "kmp-targets-gradle-plugin",
        version = version.toString(),
    )
}

tasks.test { useJUnitPlatform() }

tasks.named("check") { dependsOn("ktfmtCheck") }
