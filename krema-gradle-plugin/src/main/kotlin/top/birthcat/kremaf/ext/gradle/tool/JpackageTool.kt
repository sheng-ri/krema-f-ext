/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.tool

import org.gradle.api.GradleException
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.config.BundleSettings
import top.birthcat.kremaf.ext.gradle.config.appJarName
import top.birthcat.kremaf.ext.gradle.domain.BundleFormat
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.appImageDirName
import top.birthcat.kremaf.ext.gradle.domain.executableSuffix
import java.nio.file.Files
import java.nio.file.Path

/** jpackage 适配：拼命令执行两类流程（产 app-image / 包平台安装器），工具取自 JDK toolchain 而非 PATH，宿主平台由调用方传入。 */
class JpackageTool(
    private val execOperations: ExecOperations,
) {
    /**
     * 产出未压缩、可直接运行的 app-image（bundle 的底座）。
     * 重建前会清空已存在的 app-image 目录：jpackage 遇同名目录报「目录已存在」，
     * 而它生成的启动器带 ReadOnly 属性，deleteRecursively 删不掉只读文件，
     * 目录因删不空而残留，下次构建会卡死在「目录已存在」。
     */
    @Throws(GradleException::class)
    fun appImage(
        binDir: Path,
        platform: Platform,
        settings: BundleSettings,
        appLibDir: Path,
        runtimeImageDir: Path,
        dest: Path,
    ) {
        Files.createDirectories(dest)
        dest
            .resolve(platform.appImageDirName(settings.appName))
            .toFile()
            .let { appImage ->
                appImage.walkTopDown().forEach { it.setWritable(true) }
                appImage.deleteRecursively()
            }
        val command =
            buildList {
                add(jpackage(binDir))
                addAll(
                    listOf(
                        "--type",
                        "app-image",
                        "--name",
                        settings.appName,
                        "--input",
                        appLibDir.toString(),
                        "--main-jar",
                        appJarName(settings),
                        "--main-class",
                        settings.mainClass,
                        "--runtime-image",
                        runtimeImageDir.toString(),
                        "--dest",
                        dest.toString(),
                    ),
                )
                // --java-options 须各占一参：值含空格才不会被拆散。
                addAll(javaOptions(platform).flatMap { listOf("--java-options", it) })
                settings.icon?.let { addAll(listOf("--icon", it.toString())) }
            }
        run(command, "jpackage 生成 app-image 失败")
    }

    /** 把现成 app-image 包成本平台的可安装件（平台 task 的 jpackage 分支）。 */
    @Throws(GradleException::class)
    fun installer(
        binDir: Path,
        format: BundleFormat,
        settings: BundleSettings,
        appImageDir: Path,
        dest: Path,
        icon: Path?,
    ) {
        val type =
            format.jpackageType
                ?: throw GradleException("格式 $format 没有 jpackage type，不该走到安装器分支")
        val command =
            buildList {
                add(jpackage(binDir))
                addAll(
                    listOf(
                        "--type",
                        type,
                        "--name",
                        settings.appName,
                        "--dest",
                        dest.toString(),
                        "--app-image",
                        appImageDir.toString(),
                    ),
                )
                icon?.let { addAll(listOf("--icon", it.toString())) }
            }
        run(command, "jpackage 生成 ${format.id} 失败")
    }

    private fun jpackage(binDir: Path): String = binDir.resolve("jpackage" + executableSuffix()).toString()

    // macOS 应用进程须跑主线程，缺此项启动器起不来。
    private fun javaOptions(platform: Platform): List<String> =
        buildList {
            add("--enable-native-access=ALL-UNNAMED")
            if (platform == Platform.DARWIN) add("-XstartOnFirstThread")
        }

    @Throws(GradleException::class)
    private fun run(
        command: List<String>,
        failure: String,
    ) {
        val result = execOperations.capture(command)
        if (result.exitValue != 0) throw GradleException("$failure：\n${result.output}")
    }
}
