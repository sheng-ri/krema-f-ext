/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

/** 薄壳插件：注册 `krema` 扩展与 task 组。 */
class KremaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("krema", KremaExtension::class.java)
        extension.frontend = project.extensions.create("frontend", KremaFrontendExtension::class.java)
        extension.bundling = project.extensions.create("bundling", KremaBundlingExtension::class.java)
        // 自带 JVM 基础能力：与 Gradle 自带的 ApplicationPlugin 一样，在 apply 里挂上 java 插件，
        // 用户不必在脚本里先写别的插件。toolchain 25 由根构建在 java 插件上统一挂。
        project.pluginManager.apply(JavaPlugin::class.java)
        project.registerKremaTasks()
    }
}
