// Root of the isolated-projects sample.
// Kotlin Multiplatform is declared once with `apply false` so every subproject shares a single KGP
// classloader (mirrors samples/hello-world). Each subproject applies KGP + the
// `com.rsicarelli.kmptargets` base plugin and declares its supported set via the
// `kmpTargets { supported { … } }` DSL — all under Isolated Projects (see gradle.properties).
plugins { kotlin("multiplatform") version "2.3.21" apply false }
