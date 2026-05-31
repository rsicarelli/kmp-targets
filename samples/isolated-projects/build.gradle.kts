// Root of the isolated-projects sample.
// Kotlin Multiplatform is declared once with `apply false` so every subproject shares a single KGP
// classloader (mirrors samples/hello-world). Each subproject applies a `com.rsicarelli.kmptargets.*`
// convention plugin, which applies KGP for it and registers KMP_TARGETS ∩ supported — all under
// Isolated Projects (see gradle.properties).
plugins { kotlin("multiplatform") version "2.3.21" apply false }
