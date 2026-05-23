package com.rsicarelli.kmptargets.hierarchy

/**
 * The intermediate source-set groups Kotlin Multiplatform can introduce in a hierarchy template.
 *
 * This mirrors KGP's own group taxonomy exactly — and ONLY the native subtree, because that is the
 * only place KGP creates intermediate source sets. JVM, Android and web targets attach directly to
 * `common` with no intermediate group, so they have no entry here.
 *
 * [groupName] is the KGP group name; the source set it materializes is `groupName + "Main"/"Test"`
 * (e.g. [APPLE] → `appleMain`/`appleTest`). [parent] encodes the fixed nesting that the collapse
 * rule prunes against.
 */
internal enum class HierarchyGroup(val groupName: String) {
    NATIVE("native"),
    APPLE("apple"),
    IOS("ios"),
    MACOS("macos"),
    WATCHOS("watchos"),
    TVOS("tvos"),
    LINUX("linux"),
    MINGW("mingw"),
    ANDROID_NATIVE("androidNative");

    /** The enclosing group, or `null` for a group that attaches directly to `common`. */
    val parent: HierarchyGroup?
        get() =
            when (this) {
                NATIVE -> null
                APPLE,
                LINUX,
                MINGW,
                ANDROID_NATIVE -> NATIVE
                IOS,
                MACOS,
                WATCHOS,
                TVOS -> APPLE
            }

    /** Depth from the `common` root (NATIVE = 0, APPLE = 1, IOS = 2). */
    val depth: Int
        get() = generateSequence(parent) { it.parent }.count()
}
