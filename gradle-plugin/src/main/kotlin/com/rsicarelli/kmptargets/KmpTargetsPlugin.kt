package com.rsicarelli.kmptargets

import org.gradle.api.Plugin
import org.gradle.api.Project

public class KmpTargetsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val projectPath = target.path
        target.tasks.register("kmpTargetsHello") { task ->
            task.group = "kmp-targets"
            task.description = "Prints a hello message — bootstrap placeholder until the real selector lands."
            task.doLast {
                println("kmp-targets plugin applied to $projectPath")
            }
        }
    }
}
