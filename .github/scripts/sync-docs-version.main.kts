#!/usr/bin/env kotlin

/**
 * Documentation Version Sync Script for kmp-targets
 *
 * Updates all hardcoded kmp-targets versions in documentation and sample files to the
 * just-released version, so published docs/samples always show real coordinates.
 *
 * Usage:
 *   kotlin sync-docs-version.main.kts [version]
 *
 * Arguments:
 *   version: (optional) If not provided, reads from gradle.properties
 *
 * Examples:
 *   kotlin sync-docs-version.main.kts           # Use gradle.properties version
 *   kotlin sync-docs-version.main.kts 0.1.0     # Use specific version
 *
 * Scanned files:
 *   - docs/**/*.md (gracefully skipped while docs/ does not exist yet)
 *   - README.md
 *   - samples/**/build.gradle.kts
 *   - gradle/libs.versions.toml (kmpTargets entry — the samples' single declaration point)
 *
 * Replaced patterns:
 *   - id("com.rsicarelli.kmptargets") version "..."
 *   - com.rsicarelli:kmp-targets-gradle-plugin:...
 *   - kmpTargets = "..." (version catalog entries shown in docs)
 *
 * Idempotent: re-running with the same version rewrites nothing and exits 0.
 */

import java.io.File
import kotlin.system.exitProcess

fun readCurrentVersion(gradlePropertiesFile: File): String {
    val content = gradlePropertiesFile.readText()
    val versionLine = content.lines().find { it.startsWith("version=") }
        ?: error("version not found in gradle.properties")

    return versionLine.substringAfter("version=").trim()
}

fun syncVersionCatalog(projectDir: File, newVersion: String): Boolean {
    val versionCatalogFile = File(projectDir, "gradle/libs.versions.toml")
    if (!versionCatalogFile.exists()) return false

    val content = versionCatalogFile.readText()
    val pattern = Regex("""(kmpTargets\s*=\s*)"[^"]*"""")

    val updatedContent = pattern.replace(content) { """${it.groupValues[1]}"$newVersion"""" }
    if (updatedContent != content) {
        versionCatalogFile.writeText(updatedContent)
        println("✅ Updated gradle/libs.versions.toml")
        return true
    }
    return false
}

fun syncDocumentationVersions(projectDir: File, newVersion: String) {
    var filesUpdated = 0
    var totalReplacements = 0

    if (syncVersionCatalog(projectDir, newVersion)) filesUpdated++

    // Files to update
    val filesToUpdate = mutableListOf<File>()

    // Documentation files — docs/ may not exist yet (the site lands in a later issue);
    // that is fine, we just have 0 docs files to update.
    val docsDir = File(projectDir, "docs")
    if (docsDir.exists()) {
        docsDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".md") }
            .forEach { filesToUpdate.add(it) }
    }

    // README installation snippet
    val readme = File(projectDir, "README.md")
    if (readme.exists()) filesToUpdate.add(readme)

    // Sample build files that still use hardcoded versions (normally none — samples consume
    // the plugin through the version catalog synced above)
    File(projectDir, "samples").takeIf { it.exists() }?.walkTopDown()
        ?.filter { it.isFile && it.name == "build.gradle.kts" && !it.path.contains("/build/") }
        ?.forEach { filesToUpdate.add(it) }

    // Patterns to replace
    val replacementPatterns = listOf(
        // Plugin version declarations
        Regex("""(id\("com\.rsicarelli\.kmptargets"\)\s+version\s+)"[^"]*"""") to { m: MatchResult -> """${m.groupValues[1]}"$newVersion"""" },

        // Version catalog entries shown in docs
        Regex("""(kmpTargets\s*=\s*)"[^"]*"""") to { m: MatchResult -> """${m.groupValues[1]}"$newVersion"""" },

        // Maven coordinates, quoted or bare (supports alpha/beta/stable + -SNAPSHOT formats)
        Regex("""(com\.rsicarelli:kmp-targets-gradle-plugin:)[^"\s)]+""") to { m: MatchResult -> "${m.groupValues[1]}$newVersion" },
    )

    filesToUpdate.forEach { file ->
        val content = file.readText()
        var updatedContent = content
        var fileReplacements = 0

        replacementPatterns.forEach { (pattern, replacement) ->
            val matches = pattern.findAll(updatedContent).count()
            if (matches > 0) {
                updatedContent = pattern.replace(updatedContent, replacement)
                fileReplacements += matches
            }
        }

        // Only rewrite when the content actually changed — keeps re-runs a true no-op
        if (updatedContent != content) {
            file.writeText(updatedContent)
            filesUpdated++
            totalReplacements += fileReplacements
            println("✅ Updated ${file.relativeTo(projectDir)}")
        }
    }

    println("\n📊 Summary:")
    println("  Files updated: $filesUpdated")
    println("  Total replacements: $totalReplacements")
    println("  New version: $newVersion")
}

fun main(args: Array<String>) {
    val projectDir = File(".").absoluteFile
    val gradlePropertiesFile = File("gradle.properties")

    if (!gradlePropertiesFile.exists()) {
        println("Error: gradle.properties not found in current directory")
        exitProcess(1)
    }

    val newVersion = if (args.isNotEmpty()) {
        args[0]
    } else {
        readCurrentVersion(gradlePropertiesFile)
    }

    println("🔄 Syncing documentation versions to: $newVersion")

    syncDocumentationVersions(projectDir, newVersion)

    println("\n✅ Documentation sync completed!")
}

main(args)
