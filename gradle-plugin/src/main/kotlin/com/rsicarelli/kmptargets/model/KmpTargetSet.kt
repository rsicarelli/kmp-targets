package com.rsicarelli.kmptargets.model

/**
 * Immutable, value-typed selection of [KmpTarget]s.
 *
 * Behaves like a [Set] via interface delegation, so `in`, `size`, iteration and collection
 * extensions all work directly. All algebraic operations ([plus], [minus]) return new instances —
 * instances are never mutated.
 *
 * Members of this set are `data object` singletons, which makes the value Serializable as-is and
 * therefore safe to capture in Gradle's configuration cache without further work.
 */
@JvmInline
public value class KmpTargetSet private constructor(public val members: Set<KmpTarget>) :
    Iterable<KmpTarget> {

    public val size: Int
        get() = members.size

    public fun isEmpty(): Boolean = members.isEmpty()

    public fun isNotEmpty(): Boolean = members.isNotEmpty()

    public operator fun contains(target: KmpTarget): Boolean = target in members

    override fun iterator(): Iterator<KmpTarget> = members.iterator()

    public operator fun plus(other: KmpTarget): KmpTargetSet = KmpTargetSet(members + other)

    public operator fun plus(other: KmpTargetSet): KmpTargetSet =
        KmpTargetSet(members + other.members)

    public operator fun minus(other: KmpTarget): KmpTargetSet = KmpTargetSet(members - other)

    public operator fun minus(other: KmpTargetSet): KmpTargetSet =
        KmpTargetSet(members - other.members)

    /**
     * Returns the intersection of this set with [other] — the targets present in both. Defined as a
     * member (not an extension) so it shadows the stdlib `Iterable.intersect`, which would
     * otherwise win and return a plain `Set` because [KmpTargetSet] is [Iterable]. This is the
     * operation the plugin uses to compute `selection ∩ supported`.
     */
    public infix fun intersect(other: KmpTargetSet): KmpTargetSet =
        KmpTargetSet(members intersect other.members)

    override fun toString(): String =
        members.joinToString(prefix = "KmpTargetSet(", postfix = ")") { it.id }

    public companion object {

        /** No targets — a neutral starting point for building a set up with `+`. */
        public val empty: KmpTargetSet = KmpTargetSet(emptySet())

        /** Every target the plugin ships. */
        public val all: KmpTargetSet = KmpTargetSet(KmpTarget.all)

        /** The three iOS leaves: device arm64, simulator arm64, simulator x64. */
        public val appleMobile: KmpTargetSet =
            KmpTargetSet(
                setOf(
                    KmpTarget.Native.Apple.Ios.Arm64,
                    KmpTarget.Native.Apple.Ios.SimulatorArm64,
                    KmpTarget.Native.Apple.Ios.X64,
                )
            )

        /** The two macOS leaves: arm64 and x64. */
        public val appleDesktop: KmpTargetSet =
            KmpTargetSet(
                setOf(KmpTarget.Native.Apple.Macos.Arm64, KmpTarget.Native.Apple.Macos.X64)
            )

        /** All five watchOS leaves. */
        public val appleWatch: KmpTargetSet =
            KmpTargetSet(
                setOf(
                    KmpTarget.Native.Apple.Watchos.Arm64,
                    KmpTarget.Native.Apple.Watchos.Arm32,
                    KmpTarget.Native.Apple.Watchos.X64,
                    KmpTarget.Native.Apple.Watchos.SimulatorArm64,
                    KmpTarget.Native.Apple.Watchos.DeviceArm64,
                )
            )

        /** The three tvOS leaves: device arm64, simulator arm64, simulator x64. */
        public val appleTv: KmpTargetSet =
            KmpTargetSet(
                setOf(
                    KmpTarget.Native.Apple.Tvos.Arm64,
                    KmpTarget.Native.Apple.Tvos.X64,
                    KmpTarget.Native.Apple.Tvos.SimulatorArm64,
                )
            )

        /** Every Apple platform: iOS, macOS, watchOS, tvOS. */
        public val apple: KmpTargetSet =
            KmpTargetSet(
                appleMobile.members + appleDesktop.members + appleWatch.members + appleTv.members
            )

        /** The two Linux leaves: x64 and arm64. */
        public val linux: KmpTargetSet =
            KmpTargetSet(setOf(KmpTarget.Native.Linux.X64, KmpTarget.Native.Linux.Arm64))

        /** Windows on x86-64, via the MinGW toolchain. */
        public val mingw: KmpTargetSet = KmpTargetSet(setOf(KmpTarget.Native.Mingw.X64))

        /** The four Android NDK native leaves: arm32, arm64, x86, x64. */
        public val androidNative: KmpTargetSet =
            KmpTargetSet(
                setOf(
                    KmpTarget.Native.AndroidNative.Arm32,
                    KmpTarget.Native.AndroidNative.Arm64,
                    KmpTarget.Native.AndroidNative.X86,
                    KmpTarget.Native.AndroidNative.X64,
                )
            )

        /**
         * Every Kotlin/Native target: Apple, Linux, MinGW, Android Native. Excludes JVM and web.
         */
        public val native: KmpTargetSet =
            KmpTargetSet(apple.members + linux.members + mingw.members + androidNative.members)

        /** The three web leaves: `js`, `wasmJs`, `wasmWasi`. */
        public val web: KmpTargetSet =
            KmpTargetSet(setOf(KmpTarget.Web.Js, KmpTarget.Web.WasmJs, KmpTarget.Web.WasmWasi))

        /** The two JVM-backed leaves: `androidTarget` and `jvm`. */
        public val jvmFamily: KmpTargetSet =
            KmpTargetSet(setOf(KmpTarget.Jvm.Android, KmpTarget.Jvm.Desktop))

        /**
         * The canonical "mobile app" shape: Android plus all three iOS leaves. Android only
         * registers if the user's selection actually includes it, so this preset does not force AGP
         * onto consumers.
         */
        public val mobile: KmpTargetSet =
            KmpTargetSet(setOf<KmpTarget>(KmpTarget.Jvm.Android) + appleMobile.members)

        /** Builds a set from explicit leaves — the low-level escape hatch behind the presets. */
        public fun of(vararg targets: KmpTarget): KmpTargetSet = KmpTargetSet(targets.toSet())
    }
}
