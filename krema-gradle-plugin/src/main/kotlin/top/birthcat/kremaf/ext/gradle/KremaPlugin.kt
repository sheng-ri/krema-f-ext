/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import top.birthcat.kremaf.ext.gradle.config.KremaBundlingExtension
import top.birthcat.kremaf.ext.gradle.config.KremaExtension
import top.birthcat.kremaf.ext.gradle.config.KremaFrontendExtension
import top.birthcat.kremaf.ext.gradle.config.KremaRuntimeExtension

/** 薄壳插件：注册 `krema` 扩展与 task 组。 */
class KremaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("krema", KremaExtension::class.java)
        extension.frontend = project.extensions.create("frontend", KremaFrontendExtension::class.java)
        extension.bundling = project.extensions.create("bundling", KremaBundlingExtension::class.java)
        extension.runtime = project.extensions.create("runtime", KremaRuntimeExtension::class.java)
        // 本插件配置期就要读 sourceSets / runtimeClasspath / JavaPluginExtension（见 kremaApplicationClasspath、
        // targetBytecodeVersion），这些只有 java 插件提供。这里主动 apply，用户只挂 krema 即可，不必先写 java/kotlin。
        project.pluginManager.apply(JavaPlugin::class.java)
        project.registerKremaTasks()
    }
}
