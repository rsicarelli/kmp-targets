package com.rsicarelli.kmptargets.model

/**
 * One target this plugin actually registered with KGP (issue #52): the model [leaf] plus the Gradle
 * target [name][gradleName] registration really used — sourced from the `KotlinTarget` KGP
 * returned, never re-derived from the leaf. That keeps it truthful for a renamed jvm leaf
 * (`targetName(jvm, "desktop")` → `gradleName == "desktop"`), so per-target wiring works without
 * the consumer touching KGP types:
 * ```kotlin
 * // Before — KGP internals plus hand-rolled name mangling:
 * kotlin.targets.withType<KotlinNativeTarget>()
 *     .matching { it.konanTarget.family == Family.IOS }
 *     .forEach { add("ksp${it.targetName.replaceFirstChar(Char::titlecase)}", dep) }
 *
 * // After — the carrier delivered by KmpTargetsExtension.onRegistered:
 * kmpTargets.onRegistered { add("ksp${it.gradleNameCapitalized}", dep) }
 * ```
 *
 * Holds only a `data object` leaf and a `String` — immutable and configuration-cache safe by
 * construction. Like everything around eager registration, it is a configuration-time value: don't
 * store it in task state you don't own.
 */
public data class RegisteredTarget(val leaf: KmpTarget, val gradleName: String) {

    /**
     * [gradleName] with its first char title-cased, the shape Gradle configuration names mangle
     * target names into — `"iosArm64"` → `"IosArm64"`, ready for
     * `"ksp${it.gradleNameCapitalized}"`. Computed from [gradleName], so the two can never
     * disagree.
     */
    public val gradleNameCapitalized: String
        get() = gradleName.replaceFirstChar(Char::titlecase)
}
