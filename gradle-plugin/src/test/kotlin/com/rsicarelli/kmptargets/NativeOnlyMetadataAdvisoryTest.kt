package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure decision/message facts of the native-only-metadata advisory (#72): a module that supports
 * the JVM family (`androidTarget`/`jvm`) yet registered no JVM-family leaf — while OTHER targets
 * did register — leaves commonMain a JVM-less shared fragment. The platform klibs compile, but the
 * `*KotlinMetadata*` compilations reject JVM-flavored constructs (`@JvmInline` and friends): klibs
 * build, metadata fails. The predicate is host-free (registration is host-blind) and leaf-based
 * (the renamed jvm leaf still counts). The registered-non-empty conjunct makes it disjoint from the
 * inert-module advisory (#71) by construction — exactly one of the two fires per module state. No
 * Gradle here; the wiring (warnOrFail + dedup + ordering) is proven by
 * [NativeOnlyMetadataAdvisoryInProcessTest].
 */
class NativeOnlyMetadataAdvisoryTest {

    private val android = KmpTarget.Jvm.Android
    private val jvm = KmpTarget.Jvm.Desktop
    private val iosArm64 = KmpTarget.Native.Apple.Ios.Arm64
    private val wasmJs = KmpTarget.Web.WasmJs

    @Test
    fun `given the jvm family supported and only a native leaf registered when the policy is computed then it flags`() {
        assertTrue(
            shouldWarnNativeOnlyMetadata(
                supported = KmpTargetSet.of(android, jvm, iosArm64),
                registered = KmpTargetSet.of(iosArm64),
            )
        )
    }

    @Test
    fun `given the jvm leaf registered when the policy is computed then it does not flag`() {
        assertFalse(
            shouldWarnNativeOnlyMetadata(
                supported = KmpTargetSet.of(jvm, iosArm64),
                registered = KmpTargetSet.of(jvm, iosArm64),
            )
        )
    }

    @Test
    fun `given only androidTarget registered when the policy is computed then it does not flag`() {
        // The AGP-applied seam (AGP cannot be applied in-process): Android is a JVM platform, so a
        // commonMain spanning it is not JVM-less — any single JVM-family leaf keeps the fragment
        // JVM-flavored, android-only included.
        assertFalse(
            shouldWarnNativeOnlyMetadata(
                supported = KmpTargetSet.of(android, iosArm64),
                registered = KmpTargetSet.of(android, iosArm64),
            )
        )
    }

    @Test
    fun `given no jvm-family leaf in supported when the policy is computed then it does not flag`() {
        // A pure-native/web module never promised a JVM-flavored commonMain — there is no trap.
        assertFalse(
            shouldWarnNativeOnlyMetadata(
                supported = KmpTargetSet.of(iosArm64, wasmJs),
                registered = KmpTargetSet.of(iosArm64),
            )
        )
    }

    @Test
    fun `given jvm family supported and zero registrations when both policies are computed then only inert flags`() {
        // Disjointness with #71 by construction: registered-empty is the inert state (whole module
        // dead), registered-non-empty-but-JVM-less is this one (alive with a doomed metadata
        // compilation). Exactly one fires for any module state.
        assertFalse(
            shouldWarnNativeOnlyMetadata(
                supported = KmpTargetSet.of(jvm),
                registered = KmpTargetSet.empty,
            )
        )
        assertTrue(shouldWarnInertModule(supportsDeclared = true, registered = KmpTargetSet.empty))
    }

    @Test
    fun `given an android-only overlap skipped by the AGP guard when both policies are computed then android-without-AGP and native-only both flag`() {
        // The intended composition: android-without-AGP diagnoses the cause (the leaf was skipped,
        // #51), native-only-metadata names the consequence (ios registered, the fragment is
        // JVM-less). Keying off resolvedSupported() — not the active overlap — is what makes the
        // skipped leaf still count as "supports JVM, registered none".
        assertTrue(
            shouldWarnAndroidWithoutAgp(
                active = KmpTargetSet.of(android, iosArm64),
                androidPluginApplied = false,
            )
        )
        assertTrue(
            shouldWarnNativeOnlyMetadata(
                supported = KmpTargetSet.of(android, iosArm64),
                registered = KmpTargetSet.of(iosArm64),
            )
        )
    }

    @Test
    fun `given only a web leaf registered when the policy is computed then it flags`() {
        // The predicate is "no JVM-providing leaf", not "natives registered": a web-only commonMain
        // is equally JVM-less, so the same metadata trap applies.
        assertTrue(
            shouldWarnNativeOnlyMetadata(
                supported = KmpTargetSet.of(jvm, wasmJs),
                registered = KmpTargetSet.of(wasmJs),
            )
        )
    }

    @Test
    fun `given a host-impossible native leaf registered when the policy is computed then it still flags`() {
        // Host-blindness: registration is host-independent and the predicate never consults the
        // host — iosArm64 registered on a Linux host is still a JVM-less fragment there.
        assertTrue(
            shouldWarnNativeOnlyMetadata(
                supported = KmpTargetSet.of(jvm, iosArm64),
                registered = KmpTargetSet.of(iosArm64),
            )
        )
    }

    @Test
    fun `given a module path when the warning is built then it names the JVM family the metadata symptom and the scoped gate`() {
        val message = nativeOnlyMetadataWarning(":shared")
        assertContains(message, ":shared")
        assertContains(message, "JVM family")
        // The greppable symptom: a user arriving from the raw compiler error must find this.
        assertContains(message, "@JvmInline")
        assertContains(message, "*KotlinMetadata*")
        assertContains(message, "klibs")
        assertContains(message, "registered(jvmFamily).isEmpty()")
        // Deliberately set-free: the message is cause-agnostic (a narrowed lane and an AGP skip
        // fire it alike), so naming the selection or supported sets would mislead in at least one
        // of those cases.
        assertFalse("[" in message, message)
    }
}
