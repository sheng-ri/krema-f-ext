/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.domain

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import java.io.File

/** 主 source set 的编译产物与运行期 classpath，供把应用进程打进 bundle。 */
data class KremaApplicationClasspath(
    val classesDirs: FileCollection,
    val resourcesDir: File?,
    val runtimeClasspath: Configuration,
)

/**
 * 应用进程运行所需的 classpath 来源。
 *
 * 插件不假设编译方言：Java 与 Kotlin 的产出都落在 `sourceSets["main"]` 的输出上，
 * 因此直接取 Gradle 标准位置，不认插件自造的源码目录。
 */
fun Project.kremaApplicationClasspath(): KremaApplicationClasspath {
    val main = extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")
    return KremaApplicationClasspath(
        classesDirs = main.output.classesDirs,
        resourcesDir = main.output.resourcesDir,
        runtimeClasspath = configurations.getByName("runtimeClasspath"),
    )
}
