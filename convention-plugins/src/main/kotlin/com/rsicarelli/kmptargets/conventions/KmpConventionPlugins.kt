package com.rsicarelli.kmptargets.conventions

import com.rsicarelli.kmptargets.KmpTargets
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugins that declare a module's supported target shape by its plugin id. Each one is a
 * one-liner over [KmpTargets.applyConvention], which applies the Kotlin Multiplatform plugin and
 * the core kmp-targets plugin, declares the baked supported set, and registers `supported ∩
 * KMP_TARGETS`.
 *
 * Applying more than one composes: the supported sets union (e.g. `.mobile` + `.web`).
 */
public class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = KmpTargets.applyConvention(target, KmpTargetSet.all)
}

public class KmpMobileConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        KmpTargets.applyConvention(target, KmpTargetSet.mobile)
}

public class KmpAppleConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        KmpTargets.applyConvention(target, KmpTargetSet.apple)
}

public class KmpJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit =
        KmpTargets.applyConvention(target, KmpTargetSet.of(KmpTarget.Jvm.Desktop))
}

public class KmpWebConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = KmpTargets.applyConvention(target, KmpTargetSet.web)
}
