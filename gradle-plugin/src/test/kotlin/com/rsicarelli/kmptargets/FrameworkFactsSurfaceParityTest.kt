package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.doctor.formatKmpTargetsDoctor
import com.rsicarelli.kmptargets.info.formatKmpTargetsInfo
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.parser.presetNames
import kotlin.test.assertContains
import org.junit.jupiter.api.Test

/**
 * Cross-surface parity (#107): `kmpTargetsInfo` and `kmpTargetsDoctor` render the framework facts —
 * declared name, attached leaf ids, buildTypes, xcframework on/off — from the SAME shared
 * providers, so the two surfaces can never drift. The in-process suites pin that both read the same
 * extension state; this pins that, given identical facts, both formatters print byte-identical fact
 * lines.
 */
class FrameworkFactsSurfaceParityTest {

    @Test
    fun `given identical framework facts when both surfaces render then they print the same name attached buildTypes and xcframework`() {
        val attached = listOf("iosArm64", "iosSimulatorArm64")
        val buildTypes = listOf("DEBUG", "RELEASE")

        val info =
            formatKmpTargetsInfo(
                projectPath = ":shared",
                selectionIds = attached,
                supportedIds = attached,
                supportsDeclared = true,
                registeredIds = attached,
                originLabel = "built-in default (all)",
                presetNames = presetNames,
                leafIds = KmpTarget.all.map { it.id }.sorted(),
                hostImpossibleIds = emptyList(),
                hostLabel = "",
                deprecatedIds = emptyList(),
                frameworkDeclared = true,
                frameworkBaseName = "KotlinShared",
                frameworkAttachedIds = attached,
                frameworkBuildTypes = buildTypes,
                frameworkXcframework = true,
            )
        val doctor =
            formatKmpTargetsDoctor(
                projectPath = ":shared",
                supportsDeclared = true,
                selectionIds = attached,
                supportedIds = attached,
                registeredIds = attached,
                emptyOverlap = false,
                inertModule = false,
                nativeOnlyMetadata = false,
                androidWithoutAgp = false,
                hostImpossibleIds = emptyList(),
                hostLabel = "",
                registeredDeprecatedIds = emptyList(),
                singleTargetKsp = false,
                jvmRegisteredAs = null,
                closureDepCount = 0,
                closureGaps = emptyList(),
                frameworkDeclared = true,
                frameworkBaseName = "KotlinShared",
                frameworkAttachedIds = attached,
                frameworkBuildTypes = buildTypes,
                frameworkXcframework = true,
            )

        listOf(info, doctor).forEach { output ->
            assertContains(output, "name:        KotlinShared")
            assertContains(output, "attached:    iosArm64, iosSimulatorArm64")
            assertContains(output, "buildTypes:  DEBUG, RELEASE")
            assertContains(output, "xcframework: on")
        }
    }
}
