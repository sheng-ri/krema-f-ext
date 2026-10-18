/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.domain

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import java.io.File

/** 主 source set 的编译产物与运行期 classpath。 */
internal data class KremaApplicationClasspath(
    val classesDirs: FileCollection,
    val resourcesDir: File?,
    val runtimeClasspath: Configuration,
)

/** 取标准 main source set，兼容 Java 与 Kotlin 的产出位置。 */
internal fun Project.kremaApplicationClasspath(): KremaApplicationClasspath {
    val main = extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")
    return KremaApplicationClasspath(
        classesDirs = main.output.classesDirs,
        resourcesDir = main.output.resourcesDir,
        runtimeClasspath = configurations.getByName("runtimeClasspath"),
    )
}
