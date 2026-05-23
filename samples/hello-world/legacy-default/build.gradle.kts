plugins {
    // Same convention plugin and same KMP_TARGETS as :shared-core, but this module opts OUT of the
    // minimal template. KGP then applies its default hierarchy, so the two iOS leaves get the full
    // redundant chain — `iosMain` + `appleMain` + `nativeMain` — instead of just `iosMain`. This is
    // the side-by-side proof of what the feature removes.
    id("com.rsicarelli.kmptargets.library") version "0.1.0-SNAPSHOT"
}

kmpTargets {
    // Project-level override (wins over the global `kmptargets.hierarchyTemplate` default of true).
    hierarchyTemplate.set(false)
}
