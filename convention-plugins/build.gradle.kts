plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktfmt)
    `java-gradle-plugin`
    `maven-publish`
}

kotlin { jvmToolchain(23) }

ktfmt { kotlinLangStyle() }

dependencies {
    // The core plugin supplies KmpTargets.applyConvention + the type model.
    api(project(":gradle-plugin"))
    // KGP is applied at runtime by each convention plugin, so it must be on their runtime classpath
    // (and, transitively, on consumers' buildscript classpath) — not compileOnly.
    implementation(libs.kotlin.gradlePlugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.gradlePlugin)
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
}

gradlePlugin {
    website.set("https://github.com/rsicarelli/kmp-targets")
    vcsUrl.set("https://github.com/rsicarelli/kmp-targets.git")
    val conventions =
        listOf(
            Triple("kmpLibrary", "library", "KmpLibraryConventionPlugin") to
                "all shipped Kotlin Multiplatform targets",
            Triple("kmpMobile", "mobile", "KmpMobileConventionPlugin") to
                "Android + all iOS (the mobile-app shape)",
            Triple("kmpApple", "apple", "KmpAppleConventionPlugin") to
                "all Apple platforms (iOS + macOS + watchOS + tvOS)",
            Triple("kmpJvm", "jvm", "KmpJvmConventionPlugin") to "JVM (desktop/server)",
            Triple("kmpWeb", "web", "KmpWebConventionPlugin") to "JS + Wasm",
        )
    plugins {
        conventions.forEach { (coords, desc) ->
            val (name, idSuffix, implClass) = coords
            create(name) {
                id = "com.rsicarelli.kmptargets.$idSuffix"
                implementationClass = "com.rsicarelli.kmptargets.conventions.$implClass"
                displayName = "KMP Targets — $idSuffix"
                description =
                    "Declares this module supports $desc; intersects the KMP_TARGETS selection against it."
                tags.set(listOf("kotlin", "kmp", "multiplatform", "convention", idSuffix))
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "kmp-targets-conventions"
        }
    }
}

tasks.test { useJUnitPlatform() }

tasks.named("check") { dependsOn("ktfmtCheck") }
