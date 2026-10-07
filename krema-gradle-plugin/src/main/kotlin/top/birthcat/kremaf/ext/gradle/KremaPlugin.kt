/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/** 薄壳插件：注册 `krema` 扩展与 task 组。 */
class KremaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("krema", KremaExtension::class.java)
    }
}
