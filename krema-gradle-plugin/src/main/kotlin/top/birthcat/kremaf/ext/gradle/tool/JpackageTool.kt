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

/**
 * jpackage 适配：把应用打成未压缩 app-image，或把现成 app-image 包成平台安装器。
 *
 * 两条路径都是「拼命令 + 执行 + 捕获 + 非零退出即失败」同形流程，差异只在参数，故放一起。
 * 工具取自 JDK toolchain 的 bin 目录而非 PATH：用户用别的 JDK 构建时 PATH 上的 jpackage 可能不存在。
 * [appImage] 的宿主平台由调用方传入：平台包 task 与 bundle task 本就对宿主平台做不同处理，这里不该自作主张再解析一次。
 */
class JpackageTool(
    private val execOperations: ExecOperations,
) {
    /** 产出未压缩、可直接运行的 app-image（bundle 的底座）。 */
    @Throws(GradleException::class)
    fun appImage(
        binDir: Path,
        platform: Platform,
        settings: BundleSettings,
        appLibDir: Path,
        runtimeImageDir: Path,
        dest: Path,
    ) {
        // jpackage 见到已存在的 app-image 目录就报「目录已存在」；该目录是本 task 声明的产物，重建前清空安全。
        // 先去只读再删：jpackage 生成的启动器带 ReadOnly 属性，而 deleteRecursively 删不掉只读文件 ——
        // 表现是 app/ 与 runtime/ 都清干净了、只剩启动器，目录因删不空而留着，下一次构建就此卡死在「目录已存在」。
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
                // 每个 --java-options 各占一个参数：值里带空格时不会被拆开，jpackage 才认得整条选项。
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

    // macOS 的应用进程必须跑在主线程上，缺这条选项启动器起不来。
    private fun javaOptions(platform: Platform): List<String> =
        buildList {
            add("--enable-native-access=ALL-UNNAMED")
            if (platform == Platform.DARWIN) add("-XstartOnFirstThread")
        }

    private fun run(
        command: List<String>,
        failure: String,
    ) {
        val result = execOperations.capture(command)
        if (result.exitValue != 0) throw GradleException("$failure：\n${result.output}")
    }
}
