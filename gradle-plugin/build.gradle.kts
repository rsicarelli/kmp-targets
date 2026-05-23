plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktfmt)
    `java-gradle-plugin`
    `maven-publish`
}

kotlin { jvmToolchain(23) }

ktfmt { kotlinLangStyle() }

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.gradlePlugin)
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
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
                "Dynamically select which Kotlin Multiplatform targets to build via the KMP_TARGETS Gradle property."
            tags.set(listOf("kotlin", "kmp", "multiplatform", "build", "ios", "android"))
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "kmp-targets-gradle-plugin"
        }
    }
}

tasks.test { useJUnitPlatform() }

tasks.named("check") { dependsOn("ktfmtCheck") }
