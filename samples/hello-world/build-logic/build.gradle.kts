plugins { `kotlin-dsl` }

dependencies {
    // The kmp-targets plugin under test (from mavenLocal). `implementation` so it reaches the
    // consuming module's plugin classpath (the convention applies it by id) and so this build can
    // reference its DSL types. kmp-targets declares KGP `compileOnly`, so this does NOT drag a second
    // copy of KGP onto the consumer.
    implementation("com.rsicarelli:kmp-targets-gradle-plugin:0.1.0-SNAPSHOT")
    // KGP types for compile only. KGP itself is applied by each consuming module's
    // `kotlin("multiplatform")`, keeping a single shared KGP classloader in the sample.
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins {
        create("kmptargetsModule") {
            id = "kmptargets.module"
            implementationClass = "KmpModulePlugin"
        }
    }
}
