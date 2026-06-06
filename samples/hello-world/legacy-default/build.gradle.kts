plugins {
    // Same DSL flow and same kmptargets.targets as :shared-core, but this module opts OUT of the
    // minimal
    // template. KGP then applies its default hierarchy, so the two iOS leaves get the full
    // redundant
    // chain — `iosMain` + `appleMain` + `nativeMain` — instead of just `iosMain`. This is the
    // side-by-side proof of what the feature removes. It still `supports { all }`, like
    // :shared-core.
    kotlin("multiplatform")
    alias(libs.plugins.kmpTargets)
}

kmpTargets {
    // Project-level override (wins over the global `kmptargets.hierarchyTemplate` default of true).
    // Set before `supports`, since `supports` applies the template as part of eager registration.
    hierarchyTemplate.set(false)
    supports { all }
}
