/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.config.BundleSettings
import top.birthcat.kremaf.ext.gradle.config.appJarName
import top.birthcat.kremaf.ext.gradle.config.resolveBundleIcon
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.appImageDirName
import top.birthcat.kremaf.ext.gradle.domain.executableSuffix
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * 用 jpackage 产出未压缩、可直接运行的 app-image 目录（`build/krema/bundle`）。
 *
 * 这是所有平台包的共同底座：平台格式 task 复用这份 app-image，不再从 `--input lib` 重打一遍。
 * 前端资源与后端聚合都在 `kremaBuild` 完成，这里只做平台打包，不碰任何资源。
 */
@CacheableTask
abstract class KremaBundleTask : KremaProjectTask() {
    /** `kremaBuild` 聚好的 `lib/` 目录：app jar + 依赖 jar + assets jar。 */
    @get:InputDirectory
    abstract val appLibDir: DirectoryProperty

    /** jlink 裁出的运行时镜像，即 jpackage 的 `--runtime-image`。 */
    @get:InputDirectory
    abstract val runtimeImageDir: DirectoryProperty

    /** jpackage 所在的可执行文件目录，取自 JDK toolchain 而非 PATH。 */
    @get:Internal
    abstract val jdkBinDir: Property<String>

    /** 三个按平台的图标，优先级高于 `[bundle].icon`。文件不存在即跳过。 */
    @get:Internal
    abstract val macIcon: RegularFileProperty

    @get:Internal
    abstract val winIcon: RegularFileProperty

    @get:Internal
    abstract val linuxIcon: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun bundle() {
        val settings = bundleSettings()
        val icon =
            resolveBundleIcon(
                platform = Platform.of(targetPlatform()),
                macIcon = macIcon.orNull?.asFile?.toPath(),
                windowsIcon = winIcon.orNull?.asFile?.toPath(),
                linuxIcon = linuxIcon.orNull?.asFile?.toPath(),
                tomlIcon = settings.icon,
            )
        runJpackage(appLibDir.get().asFile.toPath(), settings.copy(icon = icon))
    }

    private fun targetPlatform(): String {
        // app-image 落盘平台即宿主平台；图标按它选。
        return System.getProperty("os.name")
    }

    private fun runJpackage(
        libDir: Path,
        settings: BundleSettings,
    ) {
        val platform = Platform.of(System.getProperty("os.name"))
        val dest = outputDir.get().asFile.toPath()
        Files.createDirectories(dest)
        // jpackage 见到已存在的 app-image 目录就报「目录已存在」；该目录是本 task 声明的产物，重建前清空是安全的。
        //
        // 先去只读再删：jpackage 生成的启动器带 ReadOnly 属性，而 deleteRecursively 删不掉只读文件 ——
        // 表现是 app/ 与 runtime/ 都清干净了、只剩启动器，目录因删不空而留着，下一次构建就此卡死在「目录已存在」。
        val appImage = dest.resolve(platform.appImageDirName(settings.appName)).toFile()
        appImage.walkTopDown().forEach { it.setWritable(true) }
        appImage.deleteRecursively()

        val command =
            mutableListOf(
                Path.of(jdkBinDir.get()).resolve("jpackage" + executableSuffix()).toString(),
                "--type",
                "app-image",
                "--name",
                settings.appName,
                "--input",
                libDir.toString(),
                "--main-jar",
                appJarName(settings),
                "--main-class",
                settings.mainClass,
                "--runtime-image",
                runtimeImageDir.get().asFile.absolutePath,
                "--dest",
                dest.toString(),
            )
        // 每个 --java-options 各占一个参数：值里带空格时不会被拆开，jpackage 才认得整条选项。
        javaOptions(platform).forEach { option -> command += listOf("--java-options", option) }
        // 图标：解析出来才加，缺失不报错。
        settings.icon?.let { command += listOf("--icon", it.toString()) }

        val captured = ByteArrayOutputStream()
        val exit =
            execOperations
                .exec { spec ->
                    spec.commandLine(command)
                    spec.isIgnoreExitValue = true
                    spec.standardOutput = captured
                    spec.errorOutput = captured
                }.exitValue
        if (exit != 0) {
            throw GradleException("jpackage 生成 app-image 失败：\n$captured")
        }
    }

    private fun javaOptions(platform: Platform): List<String> =
        buildList {
            add("--enable-native-access=ALL-UNNAMED")
            // macOS 的应用进程必须跑在主线程上，缺这条选项启动器起不来。
            if (platform == Platform.DARWIN) add("-XstartOnFirstThread")
        }
}
