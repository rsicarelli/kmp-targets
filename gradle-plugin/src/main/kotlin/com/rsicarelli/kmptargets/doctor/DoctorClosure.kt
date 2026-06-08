package com.rsicarelli.kmptargets.doctor

/**
 * One project-edge closure gap (#80): a `project(...)` dependency [dependencyPath] of the module
 * under inspection that registers **none** of the [missingLeafIds] this module needs — so this
 * module's compilations for those leaves cannot resolve the dependency's artifacts.
 *
 * A pure primitive carrier (path + sorted ids), so it threads through the configuration-cache-safe
 * task boundary and the Gradle-free [formatKmpTargetsDoctor] formatter unchanged.
 */
internal data class ClosureGap(val dependencyPath: String, val missingLeafIds: List<String>)

/**
 * One `project(...)` dependency's emitted doctor-data: its [path] and the leaf ids it actually
 * registered. Written by `kmpTargetsDoctorData` and read back by the dependent module's doctor —
 * the only thing that ever crosses the project boundary, as a file (never a peer `Project` model),
 * which is what keeps the whole closure feature configuration-cache- and Isolated-Projects-safe
 * (#80).
 */
internal data class DoctorData(val path: String, val registeredLeafIds: List<String>)

/** The leaf id of `androidTarget` — the only consumer leaf with a producer fallback (limit #2). */
private const val ANDROID_ID = "androidTarget"

/** The leaf id of the jvm leaf — `androidTarget`'s fallback producer. */
private const val JVM_ID = "jvm"

/**
 * The project-edge closure gaps for a module that needs [neededLeafIds] (the leaves it registered)
 * and declares the [dependencies] below: every dependency that registers none of a needed leaf,
 * with that leaf listed as missing.
 *
 * The single approximation (limit #2): an `androidTarget` need is satisfied by a producer that
 * registered either `androidTarget` *or* `jvm`, because Gradle's android variant resolves a
 * jvm-only library. No other fallback exists (a jvm need is *not* satisfied by an android-only
 * producer), and this only approximates Gradle's real attribute matching.
 */
internal fun computeClosureGaps(
    neededLeafIds: List<String>,
    dependencies: List<DoctorData>,
): List<ClosureGap> = dependencies.mapNotNull { dep ->
    val registered = dep.registeredLeafIds.toSet()
    val missing = neededLeafIds.filterNot { leaf -> satisfies(leaf, registered) }.sorted()
    if (missing.isEmpty()) null else ClosureGap(dep.path, missing)
}

private fun satisfies(neededLeaf: String, registered: Set<String>): Boolean =
    neededLeaf in registered || (neededLeaf == ANDROID_ID && JVM_ID in registered)

/** Serializes [data] to the flat `key=value` file `kmpTargetsDoctorData` writes. */
internal fun renderDoctorData(data: DoctorData): String = buildString {
    appendLine("path=${data.path}")
    appendLine("registered=${data.registeredLeafIds.joinToString(",")}")
}

/** Parses the file [renderDoctorData] produced; tolerant of a trailing newline and blank values. */
internal fun parseDoctorData(text: String): DoctorData {
    val entries =
        text
            .lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx < 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
    val registered =
        entries["registered"].orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return DoctorData(path = entries["path"].orEmpty(), registeredLeafIds = registered)
}
