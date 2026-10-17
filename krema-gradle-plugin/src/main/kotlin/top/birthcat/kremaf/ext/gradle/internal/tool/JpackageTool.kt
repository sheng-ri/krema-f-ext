/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.tool

import org.gradle.api.GradleException
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.api.BundleFormat
import top.birthcat.kremaf.ext.gradle.api.BundleSettings
import top.birthcat.kremaf.ext.gradle.api.Platform
import top.birthcat.kremaf.ext.gradle.api.WindowsInstallerOptions
import top.birthcat.kremaf.ext.gradle.internal.domain.appImageDirName
import top.birthcat.kremaf.ext.gradle.internal.domain.executableSuffix
import java.nio.file.Files
import java.nio.file.Path

/** jpackage 适配：产 app-image 与平台安装器；工具取自 JDK toolchain 而非 PATH。 */
internal class JpackageTool(
    private val execOperations: ExecOperations,
) {
    /**
     * 产出 app-image。
     *
     * 重建前清空同名目录：启动器带只读属性，删不干净会卡在「目录已存在」。
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
                        "--app-version",
                        settings.version,
                        "--name",
                        settings.appName,
                        "--input",
                        appLibDir.toString(),
                        "--main-jar",
                        settings.appJarName,
                        "--main-class",
                        settings.mainClass,
                        "--runtime-image",
                        runtimeImageDir.toString(),
                        "--dest",
                        dest.toString(),
                    ),
                )
                // --java-options 各占一参，值含空格才不会被拆散。
                addAll(javaOptions(platform).flatMap { listOf("--java-options", it) })
                addAll(macPackageIdentifier(platform, settings.identifier))
                settings.icon?.let { addAll(listOf("--icon", it.toString())) }
                addAll(metadata(settings))
            }
        run(command, "jpackage failed to generate the app-image")
    }

    /** 把现成 app-image 包成本平台安装件。 */
    @Throws(GradleException::class)
    fun installer(
        binDir: Path,
        format: BundleFormat,
        settings: BundleSettings,
        windows: WindowsInstallerOptions,
        appImageDir: Path,
        dest: Path,
    ) {
        val type =
            format.jpackageType
                ?: throw GradleException("Format $format has no jpackage type; it should not reach the installer branch")
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
                settings.icon?.let { addAll(listOf("--icon", it.toString())) }
                addAll(macPackageIdentifier(format.platform, settings.identifier))
                addAll(metadata(settings))
                addAll(windowsFlags(windows))
            }
        run(command, "jpackage failed to generate ${format.id}")
    }

    private fun jpackage(binDir: Path) = binDir.resolve("jpackage" + executableSuffix()).toString()

    /** 描述与版权是可选项，缺省不传。 */
    private fun metadata(settings: BundleSettings) =
        buildList {
            settings.description?.let { addAll(listOf("--description", it)) }
            settings.copyright?.let { addAll(listOf("--copyright", it)) }
        }

    /** 把 bundle.windows 翻成 --win-* 参数；null 即不传，保留 jpackage 默认。 */
    private fun windowsFlags(windows: WindowsInstallerOptions) =
        buildList {
            if (windows.dirChooser == true) add("--win-dir-chooser")
            windows.helpUrl?.let { addAll(listOf("--win-help-url", it)) }
            if (windows.menu == true) add("--win-menu")
            windows.menuGroup?.let { addAll(listOf("--win-menu-group", it)) }
            if (windows.perUserInstall == true) add("--win-per-user-install")
            if (windows.shortcut == true) add("--win-shortcut")
            if (windows.shortcutPrompt == true) add("--win-shortcut-prompt")
            windows.updateUrl?.let { addAll(listOf("--win-update-url", it)) }
            windows.upgradeUuid?.let { addAll(listOf("--win-upgrade-uuid", it)) }
        }

    // macOS 应用进程须跑主线程，缺此项启动器起不来。
    private fun javaOptions(platform: Platform) =
        buildList {
            add("--enable-native-access=ALL-UNNAMED")
            if (platform == Platform.DARWIN) add("-XstartOnFirstThread")
        }

    // macOS 需显式包标识；其它平台不接受该参数。
    private fun macPackageIdentifier(
        platform: Platform,
        identifier: String,
    ) = if (platform == Platform.DARWIN) {
        listOf("--mac-package-identifier", identifier)
    } else {
        emptyList()
    }

    private fun run(
        command: List<String>,
        failure: String,
    ) {
        val result = execOperations.capture(command)
        if (result.exitValue != 0) throw GradleException("$failure: \n${result.output}")
    }
}
