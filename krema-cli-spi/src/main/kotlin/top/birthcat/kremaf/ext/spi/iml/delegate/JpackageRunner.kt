/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import top.birthcat.kremaf.ext.spi.port.BundleSettings
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.FileSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * `jpackage` 的调用。
 *
 * Windows `.exe` 与 Linux `.deb` 只差 `--type` 与平台专属开关，命令拼装收在一处，
 * 平台 bundler 只声明「我是谁」。
 *
 * `--input` 要的是一个摊平目录：jar 与原生库都放进去，jpackage 会照单收进 runtime 镜像。
 * 所以这里先把 [BundleSettings.classpath] 摊进 staging 目录，再执行 —— 顺序错了 jpackage
 * 会打出一个空壳。
 */
class JpackageRunner(
    private val commandRunner: CommandRunner,
    private val fileSystem: FileSystem,
) {
    /**
     * 打一个安装包。
     *
     * @param settings 打包输入
     * @param type jpackage 的 `--type`，如 `exe`、`deb`
     * @param destDir 产物落地目录
     * @param extraArgs 平台专属开关
     * @return jpackage 的 `--dest` 目录
     */
    fun run(
        settings: BundleSettings,
        type: String,
        destDir: Path,
        extraArgs: List<String> = emptyList(),
    ): Path {
        val classpath = settings.classpath ?: error("打包 ${settings.appName} 前必须给定 classpath")
        val workingDir = settings.outputDir.resolve(".jpackage/${settings.appName}-$type")
        val inputDir = workingDir.resolve("input")
        val dest = destDir.also { fileSystem.deleteTree(it) }
        val mainJar = stageInput(inputDir, classpath, settings)

        val jpackage = jdk().resolve(jpackageBinary())
        val exit =
            commandRunner.exec(
                buildList {
                    add(jpackage.toString())
                    add("--type")
                    add(type)
                    add("--input")
                    add(inputDir.toString())
                    add("--main-jar")
                    add(mainJar)
                    add("--main-class")
                    add(settings.mainClass)
                    add("--name")
                    add(settings.appName)
                    add("--app-version")
                    add(settings.version)
                    add("--dest")
                    add(dest.toString())
                    settings.icon?.let { icon ->
                        add("--icon")
                        add(icon.toString())
                    }
                    addAll(extraArgs)
                },
                workingDir,
            )
        check(exit == 0) { "jpackage --type $type 失败，退出码 $exit" }
        return dest
    }

    /**
     * 把 jar 与原生库摊进 `--input` 目录。
     *
     * classpath 上有同名 `$appName.jar` 就直接用；没有就用 `jar` 工具把编译输出压一个出来 ——
     * 应用自己的输出不属于自己的 `runtimeClasspath`，不兜这一刀，consumer 就得手接一条
     * 循环依赖才能打包。
     *
     * @return 主 jar 的文件名，喂给 `--main-jar`
     */
    private fun stageInput(
        inputDir: Path,
        classpath: Classpath,
        settings: BundleSettings,
    ): String {
        fileSystem.deleteTree(inputDir)
        val jars = classpath.entries.filter { it.fileName.toString().endsWith(".jar") }
        jars.forEach { jar -> fileSystem.copyFlat(jar, inputDir) }
        classpath.nativeLibDir?.let { libDir -> fileSystem.copyMatching(libDir, inputDir, NATIVE_SUFFIXES) }

        val mainJarName = "${settings.appName}.jar"
        if (jars.none { it.fileName.toString() == mainJarName }) {
            buildMainJar(inputDir.resolve(mainJarName), classpath.classOutputDir, settings.mainClass)
        }
        return mainJarName
    }

    /** 把编译输出压成 `--main-jar` 认识的那种 jar，manifest 带上主类。 */
    private fun buildMainJar(
        output: Path,
        classesDir: Path,
        mainClass: String,
    ) {
        check(Files.isDirectory(classesDir)) {
            "编译输出目录不存在：$classesDir —— 先跑一次编译，或把 krema.toml 的 [build].java_source_dir 指对"
        }
        val jar = jdk().resolve(jarBinary())
        val exit =
            commandRunner.exec(
                listOf(
                    jar.toString(),
                    "--create",
                    "--file",
                    output.toString(),
                    "--main-class",
                    mainClass,
                    "-C",
                    classesDir.toString(),
                    ".",
                ),
                classesDir,
            )
        check(exit == 0) { "jar 打包失败，退出码 $exit" }
    }

    /** `jpackage` 所在目录，优先 JDK 25，退到 PATH。 */
    private fun jdk(): Path =
        JdkResolver(commandRunner).resolveJpackage()
            ?: error("找不到 jpackage，打包要 JDK 16+ 的 jpackage")

    private fun jpackageBinary(): String = if (isWindows()) "jpackage.exe" else "jpackage"

    private fun jarBinary(): String = if (isWindows()) "jar.exe" else "jar"

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    private companion object {
        private val NATIVE_SUFFIXES = listOf(".dylib", ".dll", ".so")
    }
}
