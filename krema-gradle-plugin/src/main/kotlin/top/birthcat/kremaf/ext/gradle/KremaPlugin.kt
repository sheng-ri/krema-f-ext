/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import top.birthcat.kremaf.ext.gradle.api.KremaBundlingExtension
import top.birthcat.kremaf.ext.gradle.api.KremaExtension
import top.birthcat.kremaf.ext.gradle.api.KremaFrontendExtension
import top.birthcat.kremaf.ext.gradle.api.KremaRuntimeExtension
import top.birthcat.kremaf.ext.gradle.api.KremaWindowsExtension
import top.birthcat.kremaf.ext.gradle.internal.registerKremaTasks

/**
 * Entry point of the `top.birthcat.kremaf.ext` plugin.
 *
 * Creates the `krema { }` extension tree, applies the Java plugin that the task graph builds on,
 * and registers the Krema tasks.
 */
@Suppress("unused")
class KremaPlugin : Plugin<Project> {
    /** Wires the extensions and task graph into [project]. */
    override fun apply(project: Project) {
        val extension = project.extensions.create("krema", KremaExtension::class.java)
        extension.frontend = project.extensions.create("frontend", KremaFrontendExtension::class.java)
        extension.bundling = project.extensions.create("bundling", KremaBundlingExtension::class.java)
        extension.bundling.windows = project.extensions.create("windows", KremaWindowsExtension::class.java)
        extension.runtime = project.extensions.create("runtime", KremaRuntimeExtension::class.java)
        // task 图依赖 java 插件的 sourceSets 与 runtimeClasspath，这里代用户 apply。
        project.pluginManager.apply(JavaPlugin::class.java)
        project.registerKremaTasks()
    }
}
