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

    override fun toString(): String =
        members.joinToString(prefix = "KmpTargetSet(", postfix = ")") { it.id }

    public companion object {

        public val empty: KmpTargetSet = KmpTargetSet(emptySet())

        public val all: KmpTargetSet = KmpTargetSet(KmpTarget.all)

        public val appleMobile: KmpTargetSet =
            KmpTargetSet(
                setOf(KmpTarget.Native.Apple.Ios.Arm64, KmpTarget.Native.Apple.Ios.SimulatorArm64)
            )

        public val appleDesktop: KmpTargetSet =
            KmpTargetSet(
                setOf(KmpTarget.Native.Apple.Macos.Arm64, KmpTarget.Native.Apple.Macos.X64)
            )

        public val apple: KmpTargetSet = KmpTargetSet(appleMobile.members + appleDesktop.members)

        public val web: KmpTargetSet =
            KmpTargetSet(setOf(KmpTarget.Web.Js, KmpTarget.Web.WasmJs, KmpTarget.Web.WasmWasi))

        public val jvmFamily: KmpTargetSet =
            KmpTargetSet(setOf(KmpTarget.Jvm.Android, KmpTarget.Jvm.Desktop))

        public fun of(vararg targets: KmpTarget): KmpTargetSet = KmpTargetSet(targets.toSet())
    }
}
