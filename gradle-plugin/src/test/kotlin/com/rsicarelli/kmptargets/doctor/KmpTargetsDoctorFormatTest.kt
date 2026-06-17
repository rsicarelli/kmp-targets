package com.rsicarelli.kmptargets.doctor

import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The doctor report is a pure function of primitives (no `Project`, extension, or `KmpTargetSet`
 * reaches it), exactly like the `kmpTargetsInfo` formatter — which is what keeps the task action
 * configuration-cache safe and this formatter unit-testable without Gradle.
 *
 * Doctor *renders*, it does not *own* predicates: each `[!]` finding here corresponds to a boolean
 * the plugin already computes with the same decision function the advisory and `kmpTargetsInfo` use
 * ([com.rsicarelli.kmptargets.shouldWarnInertModule] and friends). These tests pin the explanatory
 * prose; the in-process suite pins that the booleans come from that shared state.
 */
class KmpTargetsDoctorFormatTest {

    @Test
    fun `given an inert module when formatted then it explains the cause effect and fix`() {
        val output =
            format(
                selectionIds = listOf("wasmJs"),
                supportedIds = listOf("jvm"),
                registeredIds = emptyList(),
                emptyOverlap = true,
                inertModule = true,
            )
        assertContains(output, "[!] inert module")
        assertContains(output, "commonMain metadata compilation")
        assertContains(output, "gate on registered().isEmpty()")
    }

    @Test
    fun `given a JVM-less module when formatted then it explains the metadata rejection and scoped fix`() {
        val output =
            format(
                selectionIds = listOf("iosArm64"),
                supportedIds = listOf("iosArm64", "jvm"),
                registeredIds = listOf("iosArm64"),
                nativeOnlyMetadata = true,
            )
        assertContains(output, "[!]")
        assertContains(output, "@JvmInline")
        assertContains(output, "gate on registered(jvmFamily).isEmpty()")
    }

    @Test
    fun `given an android-without-AGP module when formatted then it names the skip and the remedy`() {
        val output =
            format(
                selectionIds = listOf("androidTarget"),
                supportedIds = listOf("androidTarget"),
                registeredIds = listOf("androidTarget"),
                androidWithoutAgp = true,
                inertModule = true,
            )
        assertContains(output, "[!] androidTarget skipped")
        assertContains(output, "no Android Gradle plugin")
        assertContains(output, "before supports { }")
    }

    @Test
    fun `given a host-impossible registration when formatted then it lists the leaves and the host`() {
        val output =
            format(
                registeredIds = listOf("iosArm64", "jvm"),
                hostImpossibleIds = listOf("iosArm64"),
                hostLabel = "LINUX_X64",
            )
        assertContains(output, "[!]")
        assertContains(output, "iosArm64")
        assertContains(output, "LINUX_X64")
        assertFalse("jvm" in output.substringAfter("LINUX_X64").substringBefore("\n"), output)
    }

    @Test
    fun `given deprecated registered leaves when formatted then it names them`() {
        val output =
            format(
                registeredIds = listOf("jvm", "watchosX64"),
                registeredDeprecatedIds = listOf("watchosX64"),
            )
        assertContains(output, "[!]")
        assertContains(output, "watchosX64")
        assertContains(output, "deprecated")
    }

    @Test
    fun `given an empty overlap when formatted then it names the disjoint selection and supported sets`() {
        val output =
            format(
                selectionIds = listOf("wasmJs"),
                supportedIds = listOf("jvm"),
                registeredIds = emptyList(),
                emptyOverlap = true,
                inertModule = true,
            )
        assertContains(output, "wasmJs")
        assertContains(output, "jvm")
        assertContains(output, "matches none")
    }

    @Test
    fun `given a single-target KSP module when formatted then it explains the missing commonMain route and the fix`() {
        val output =
            format(
                selectionIds = listOf("androidTarget"),
                supportedIds = listOf("androidTarget"),
                registeredIds = listOf("androidTarget"),
                singleTargetKsp = true,
            )
        assertContains(output, "[!] single-target KSP")
        assertContains(output, "kspCommonMainKotlinMetadata")
        assertContains(output, "register a second target")
    }

    @Test
    fun `given a healthy fully registered module when formatted then it reports a clean bill and no warnings`() {
        val output = format(registeredIds = listOf("iosArm64", "jvm"))
        assertContains(output, "clean bill of health")
        assertContains(output, "iosArm64, jvm")
        assertFalse("[!]" in output, output)
    }

    @Test
    fun `given a module that never declared supports when formatted then it explains the silence is intentional`() {
        val output =
            format(
                supportsDeclared = false,
                selectionIds = listOf("jvm"),
                supportedIds = emptyList(),
                registeredIds = emptyList(),
            )
        assertContains(output, "never called supports { }")
        assertContains(output, "intentional")
        assertFalse("[!]" in output, output)
    }

    @Test
    fun `given a renamed jvm leaf when formatted then it notes the registered name without flagging a problem`() {
        val output = format(registeredIds = listOf("jvm"), jvmRegisteredAs = "desktop")
        assertContains(output, "registered as: desktop")
        assertFalse("[!]" in output, output)
    }

    @Test
    fun `given a project edge gap when formatted then it names the dependency the missing leaves and the fix`() {
        val output =
            format(
                registeredIds = listOf("iosArm64", "jvm"),
                closureDepCount = 1,
                closureGaps =
                    listOf(
                        ClosureGap(
                            dependencyPath = ":core-jvm",
                            missingLeafIds = listOf("iosArm64"),
                        )
                    ),
            )
        assertContains(output, ":core-jvm")
        assertContains(output, "iosArm64")
        assertContains(output, "Project-edge closure")
    }

    @Test
    fun `given a valid closure when formatted then the closure section reports no gaps`() {
        val output =
            format(registeredIds = listOf("jvm"), closureDepCount = 2, closureGaps = emptyList())
        assertContains(output, "Project-edge closure")
        assertContains(output, "no gaps")
        assertFalse("[!] :" in output, output)
    }

    @Test
    fun `given the closure section renders when formatted then both honest limits are printed`() {
        val output =
            format(
                registeredIds = listOf("jvm"),
                closureDepCount = 1,
                closureGaps =
                    listOf(ClosureGap(dependencyPath = ":a", missingLeafIds = listOf("jvm"))),
            )
        assertContains(output, "external")
        assertContains(output, "approximate")
    }

    @Test
    fun `given no project dependencies when formatted then no closure section appears`() {
        val output = format(registeredIds = listOf("jvm"), closureDepCount = 0)
        assertFalse("Project-edge closure" in output, output)
    }

    @Test
    fun `given identical inputs when formatted twice then the output is identical`() {
        // Config-cache contract: pure function of primitives.
        assertTrue(format() == format())
    }

    private fun format(
        projectPath: String = ":feature-mobile",
        supportsDeclared: Boolean = true,
        selectionIds: List<String> = listOf("jvm"),
        supportedIds: List<String> = listOf("jvm"),
        registeredIds: List<String> = listOf("jvm"),
        emptyOverlap: Boolean = false,
        inertModule: Boolean = false,
        nativeOnlyMetadata: Boolean = false,
        androidWithoutAgp: Boolean = false,
        hostImpossibleIds: List<String> = emptyList(),
        hostLabel: String = "",
        registeredDeprecatedIds: List<String> = emptyList(),
        singleTargetKsp: Boolean = false,
        jvmRegisteredAs: String? = null,
        closureDepCount: Int = 0,
        closureGaps: List<ClosureGap> = emptyList(),
    ): String =
        formatKmpTargetsDoctor(
            projectPath = projectPath,
            supportsDeclared = supportsDeclared,
            selectionIds = selectionIds,
            supportedIds = supportedIds,
            registeredIds = registeredIds,
            emptyOverlap = emptyOverlap,
            inertModule = inertModule,
            nativeOnlyMetadata = nativeOnlyMetadata,
            androidWithoutAgp = androidWithoutAgp,
            hostImpossibleIds = hostImpossibleIds,
            hostLabel = hostLabel,
            registeredDeprecatedIds = registeredDeprecatedIds,
            singleTargetKsp = singleTargetKsp,
            jvmRegisteredAs = jvmRegisteredAs,
            closureDepCount = closureDepCount,
            closureGaps = closureGaps,
        )
}
