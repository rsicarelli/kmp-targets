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

    /**
     * The Gradle configuration name a per-target tool publishes for this registration's **main**
     * compilation (issue #74): [prefix] + [gradleNameCapitalized] — the `ksp<Target>` grammar
     * KGP-aware tools mangle into, owned here so build-logic stops re-rolling it by hand:
     * ```kotlin
     * // Before — string mangling repeated at every call site:
     * kmpTargets.onRegistered { add("ksp${it.gradleNameCapitalized}", processor) }
     *
     * // After:
     * kmpTargets.onRegistered { add(it.configurationName("ksp"), processor) }
     * ```
     *
     * Tool-generic on purpose — [prefix] is any tool's convention (`"ksp"`, `"kapt"`); the plugin
     * is mechanism, not a KSP integration. Built on [gradleNameCapitalized], so it stays truthful
     * under a renamed jvm leaf (`targetName(jvm, "desktop")` → `configurationName("ksp") ==
     * "kspDesktop"`). See [testConfigurationName] for the test compilation's configuration.
     *
     * @throws IllegalArgumentException if [prefix] is blank.
     */
    public fun configurationName(prefix: String): String {
        require(prefix.isNotBlank()) {
            "configurationName prefix must not be blank (got \"$prefix\"); pass a tool prefix like \"ksp\"."
        }
        return prefix + gradleNameCapitalized
    }

    /**
     * The Gradle configuration name a per-target tool publishes for this registration's **test**
     * compilation (issue #74): [configurationName] + `"Test"` — the `ksp<Target>Test` grammar. This
     * is the variant that silently breaks when hardcoded: a literal `"kspJvmTest"` throws
     * *Configuration 'kspJvmTest' not found* once the jvm leaf is renamed (it becomes
     * `kspDesktopTest`), the very footgun `targetName` induces. Derived from [gradleName], so it
     * never disagrees:
     * ```kotlin
     * kmpTargets.onRegistered {
     *     add(it.configurationName("ksp"), processor)      // main compilation
     *     add(it.testConfigurationName("ksp"), processor)  // test compilation
     * }
     * ```
     *
     * Delegates to [configurationName], so the prefix rule and validation live in one place.
     *
     * @throws IllegalArgumentException if [prefix] is blank.
     */
    public fun testConfigurationName(prefix: String): String = configurationName(prefix) + "Test"
}
