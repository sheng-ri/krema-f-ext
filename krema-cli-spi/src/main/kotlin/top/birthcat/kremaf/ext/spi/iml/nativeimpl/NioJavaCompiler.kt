/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.nativeimpl

import top.birthcat.kremaf.ext.spi.iml.delegate.JdkResolver
import top.birthcat.kremaf.ext.spi.iml.delegate.executableSuffix
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.FileSystem
import top.birthcat.kremaf.ext.spi.port.JavaCompiler
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.nio.file.Files
import java.nio.file.Path

/**
 * `javac` 与 `native-image` 的调用。
 *
 * classpath 由 [top.birthcat.kremaf.ext.spi.port.ClasspathResolver] 给，编译只剩「把参数拼对」这一件事：
 * 不绑任何构建工具的目录约定。native 那半边的命令形状见 [NativeImageBuilder]。
 */
class NioJavaCompiler(
    private val commandRunner: CommandRunner,
    private val jdkResolver: JdkResolver,
    private val outputDir: Path,
    private val fileSystem: FileSystem,
    private val env: Map<String, String> = System.getenv(),
) : JavaCompiler {
    private val nativeImageBuilder = NativeImageBuilder(commandRunner, fileSystem)

    override fun compile(
        project: KremaProject,
        classpath: Classpath,
    ): Path {
        val sourceDir = project.java.sourceDir ?: project.dir.resolve(project.config.build.javaSourceDir)
        val classesDir = outputDir.resolve("classes")
        val sources = collectSources(sourceDir)
        if (sources.isEmpty()) return classesDir

        val javac = javaHome().resolve("bin/javac" + executableSuffix())
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

    override fun compileNative(
        project: KremaProject,
        classpath: Classpath,
    ): Path {
        val classesDir = compile(project, classpath)
        val tool =
            nativeImageBuilder.findTool(jdkResolver.resolve(env), env)
                ?: error("找不到 native-image —— 装上 GraalVM，或把 GRAALVM_HOME / JAVA_HOME 指向它，再或者让它进 PATH")
        val outputBinary = outputBinary(project)
        val resourceConfig = nativeImageBuilder.writeResourceConfig(project, outputDir)
        val command =
            nativeImageBuilder.command(project, classpath, classesDir, outputBinary, resourceConfig, tool)

        val exit = commandRunner.exec(command, project.dir)
        check(exit == 0) { "native-image 编译失败，退出码 $exit" }

        nativeImageBuilder.copyNativeLibrary(classpath, outputBinary)
        nativeImageBuilder.copyVCRuntime(outputBinary.parent)
        return outputBinary
    }

    private fun outputBinary(project: KremaProject): Path = outputDir.resolve(project.bundle.appName + executableSuffix())

    private fun collectSources(sourceDir: Path): List<Path> {
        if (!Files.isDirectory(sourceDir)) return emptyList()
        Files.walk(sourceDir).use { stream ->
            return stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }.toList()
        }
    }

    private fun javaHome(): Path = jdkResolver.resolve(env) ?: error("找不到 JDK 25，无法编译")

    private companion object {
        private const val RELEASE = "25"
    }
}
