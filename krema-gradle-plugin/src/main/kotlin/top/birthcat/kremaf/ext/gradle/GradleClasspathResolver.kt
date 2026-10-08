/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.file.FileCollection
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.ClasspathResolver
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.nio.file.Path

/**
 * [ClasspathResolver] 的 Gradle 实现。
 *
 * 这是中间层唯一的构建工具耦合点：`CompileClasspath` 与输出目录都由 Gradle 侧给定，
 * 编排层拿到的只有一个 [Classpath]。
 */
class GradleClasspathResolver(
    private val runtimeClasspath: FileCollection,
    private val classOutputDir: Path,
) : ClasspathResolver {
    override fun resolve(project: KremaProject): Classpath =
        Classpath(
            entries = runtimeClasspath.files.map { it.toPath() },
            classOutputDir = classOutputDir,
        )
}
