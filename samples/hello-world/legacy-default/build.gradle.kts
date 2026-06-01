plugins {
    // Same DSL flow and same KMP_TARGETS as :shared-core, but this module opts OUT of the minimal
    // template. KGP then applies its default hierarchy, so the two iOS leaves get the full redundant
    // chain — `iosMain` + `appleMain` + `nativeMain` — instead of just `iosMain`. This is the
    // side-by-side proof of what the feature removes. `supported` is left at its default of `all`.
    kotlin("multiplatform")
    id("com.rsicarelli.kmptargets") version "0.1.0-SNAPSHOT"
}

kmpTargets {
    // Project-level override (wins over the global `kmptargets.hierarchyTemplate` default of true).
    hierarchyTemplate.set(false)
}
