/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.nativeimpl

import top.birthcat.kremaf.ext.spi.iml.delegate.JdkResolver
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.FileSystem
import top.birthcat.kremaf.ext.spi.port.JavaCompiler
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * `javac` 的调用。
 *
 * **为什么必须自己写**：上游把编译绑在 `mvn package` 上（`BuildCommand.buildWithMaven`），
 * classpath 也必须从 `mvn dependency:build-classpath` 来。本模块的 classpath 由
 * [top.birthcat.kremaf.ext.spi.port.ClasspathResolver] 给，编译只剩"把参数拼对"这一件事。
 */
class NioJavaCompiler(
    private val commandRunner: CommandRunner,
    private val fileSystem: FileSystem,
    private val jdkResolver: JdkResolver,
    private val outputDir: Path,
) : JavaCompiler {
    override fun compile(
        project: KremaProject,
        classpath: Classpath,
    ): Path {
        val sourceDir = project.java.sourceDir ?: project.dir.resolve(project.config.build.javaSourceDir)
        val classesDir = outputDir.resolve("classes")
        val sources = collectSources(sourceDir)
        if (sources.isEmpty()) return classesDir

        val javaHome = jdkResolver.resolve() ?: error("找不到 JDK 25，无法编译")
        val javac = javaHome.resolve(if (isWindows()) "bin/javac.exe" else "bin/javac")
        val command =
            buildList {
                add(javac.toString())
                add("--release")
                add(RELEASE)
                add("-d")
                add(classesDir.toString())
                if (classpath.entries.isNotEmpty()) {
                    add("-cp")
                    add(classpath.joined())
                }
                addAll(sources.map(Path::toString))
            }

        val exit = commandRunner.exec(command, project.dir)
        check(exit == 0) { "javac 编译失败，退出码 $exit" }
        return classesDir
    }

    private fun collectSources(sourceDir: Path): List<Path> {
        if (!Files.isDirectory(sourceDir)) return emptyList()
        Files.walk(sourceDir).use { stream ->
            return stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }.toList()
        }
    }

    private fun isWindows(): Boolean = File.separatorChar == '\\'

    private companion object {
        private const val RELEASE = "25"
    }
}
