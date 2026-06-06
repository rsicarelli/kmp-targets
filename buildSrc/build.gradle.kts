plugins { `kotlin-dsl` }

dependencies {
    // API artifact only — deliberately NOT the full kotlin-gradle-plugin. buildSrc classes sit in
    // a parent classloader of every build script; shipping the full KGP here would put the
    // org.jetbrains.kotlin.jvm plugin on that parent classpath and break the root build's
    // version-carrying `alias(libs.plugins.kotlin.jvm)` ("plugin already on the classpath").
    // The API jar has the task/option types JvmCompatibility.kt needs and nothing more.
    implementation(libs.kotlin.gradlePluginApi)

    // Class-file reader for VerifyCompatFloorsTask: walks the built jar asserting the bytecode and
    // Kotlin-metadata floors actually hold. Gradle bundles ASM only as an internal impldep.
    implementation(libs.asm)
}
