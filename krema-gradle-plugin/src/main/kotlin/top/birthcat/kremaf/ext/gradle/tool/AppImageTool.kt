/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.tool

import org.gradle.api.GradleException
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.config.BundleSettings
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.PlatformDetector
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * 封装 AppImage 工具 `appimagetool`：定位 / 下载工具，把 app-image 目录打成 `<appName>.AppImage`。
 * 工具取自 PATH 或自动下载；下载到的是自带 FUSE 的 AppImage，无 FUSE 环境下先 `--appimage-extract` 抽出真二进制再打包。
 */
class AppImageTool(
    private val execOperations: ExecOperations,
) {
    /**
     * 把 [appImageDir] 封装成 `<appName>.AppImage` 落到 [outputDir]，返回产物路径。
     * [autoDownload] 为 false 且 PATH 上找不到工具时抛 GradleException，提示手动安装或开开关。
     */
    @Throws(GradleException::class)
    fun packageAppImage(
        appImageDir: Path,
        outputDir: Path,
        settings: BundleSettings,
        autoDownload: Boolean,
    ): Path {
        val rawTool =
            findInPath()
                ?: if (autoDownload) {
                    download(outputDir)
                } else {
                    throw GradleException(
                        "PATH 上找不到 appimagetool，且 krema.bundling.appImageToolAutoDownload 为 false。" +
                            "手动安装 appimagetool 并放进 PATH，或把该开关打开。",
                    )
                }
        // appimagetool 要 app dir 里有 `<appName>.desktop`（jpackage 的 app-image 不带）。但那是 kremaBundle 的产物，
        // 不能由本 task 改写，否则二次运行输入哈希变了、UP-TO-DATE 失效。复制到 staging 再写元信息。
        val staging = outputDir.resolve("appimage-staging")
        staging.toFile().deleteRecursively()
        Files.createDirectories(staging)
        Files.walk(appImageDir).use { stream ->
            for (from in stream) {
                if (from == appImageDir) continue
                val to = staging.resolve(appImageDir.relativize(from))
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to)
                } else {
                    Files.copy(from, to, REPLACE_EXISTING, COPY_ATTRIBUTES)
                }
            }
        }
        prepareAppImageMeta(settings, staging)
        // 下载到的是 appimagetool 自带的 AppImage，直接跑要 FUSE；多数容器 / CI 没有 FUSE。
        // 用 `--appimage-extract` 抽出内部真二进制（不挂载、不需 FUSE），后续打包走它。
        val tool = resolveAppImageTool(rawTool, outputDir)
        val out = outputDir.resolve("${settings.appName}.AppImage")
        runCommand(
            listOf(tool.toAbsolutePath().toString(), staging.toString(), out.toAbsolutePath().toString()),
            "appimagetool 封装 AppImage 失败",
        )
        // 舞台是输出区里的临时副本，打包完即删，别把整份 app-image 留在产物里。
        staging.toFile().deleteRecursively()
        return out
    }

    // appimagetool 要 app dir 里有 `.desktop`（jpackage 的 app-image 不带）。staging 是副本，可放心写。
    private fun prepareAppImageMeta(
        settings: BundleSettings,
        appDir: Path,
    ) {
        val desktop = appDir.resolve("${settings.appName}.desktop")
        Files.writeString(
            desktop,
            """
            [Desktop Entry]
            Type=Application
            Name=${settings.appName}
            Exec=bin/${settings.appName}
            Icon=${settings.appName}
            Terminal=false
            Categories=Utility;
            """.trimIndent(),
        )
        val iconPng = appDir.resolve("${settings.appName}.png")
        val from = settings.icon ?: appDir.resolve("lib/${settings.appName}.png")
        if (Files.exists(from)) Files.copy(from, iconPng, REPLACE_EXISTING)
    }

    /**
     * 解析出真正能用的 `appimagetool` 可执行文件。
     * 下载到的工具本身是 AppImage，运行需要 FUSE；而 `--appimage-extract` 只解包内嵌的 squashfs、不挂载，
     * 因此无 FUSE 也能跑，抽出的是普通 ELF 二进制，用它打包不再依赖 FUSE。已经是真二进制（PATH 里装的那种）
     * 不认 `--appimage-extract`，抽出失败就回退到原工具。
     */
    @Throws(GradleException::class)
    private fun resolveAppImageTool(
        rawTool: Path,
        outputDir: Path,
    ): Path {
        val extractDir = outputDir.resolveSibling(".appimagetool-extract")
        val inner = extractDir.resolve("squashfs-root/usr/bin/appimagetool")
        if (Files.isRegularFile(inner)) return inner
        Files.createDirectories(extractDir)
        // --appimage-extract 在工作目录内把 AppImage 解包成普通 ELF 二进制，不挂载、不需 FUSE。
        val result =
            execOperations.capture(
                listOf(rawTool.toAbsolutePath().toString(), "--appimage-extract"),
                workingDir = extractDir,
            )
        if (result.exitValue != 0) return rawTool
        if (!Files.isRegularFile(inner)) {
            throw GradleException("抽出 appimagetool 失败：\n${result.output}")
        }
        return inner
    }

    @Throws(GradleException::class)
    private fun runCommand(
        command: List<String>,
        failure: String,
    ) {
        val result = execOperations.capture(command)
        if (result.exitValue != 0) throw GradleException("$failure：\n${result.output}")
    }

    companion object {
        /** 当前平台的工具可执行文件名。 */
        private fun executableName(): String = if (PlatformDetector.detect() == Platform.WINDOWS) "appimagetool.exe" else "appimagetool"

        /**
         * 在给定目录列表里找 `appimagetool`。
         * 抽成纯函数（不读环境变量）便于单测：调用方把 `PATH` 拆好传进来即可。
         */
        fun findInPaths(paths: List<Path>): Path? {
            val name = executableName()
            return paths
                .firstOrNull { Files.isRegularFile(Path.of(it.toString(), name)) }
                ?.resolve(name)
        }

        /** 在 `PATH` 环境变量里找 `appimagetool`；找不到返回 null。 */
        fun findInPath(): Path? {
            val path = System.getenv("PATH") ?: return null
            return findInPaths(path.split(File.pathSeparator).map { Path.of(it) })
        }

        /**
         * 下载 `appimagetool` 到 [destDir]，返回落下的可执行文件。
         * 失败抛 IllegalStateException，报错给出「手动装 / 关开关」两条出路。
         */
        @Throws(IllegalStateException::class)
        fun download(destDir: Path): Path {
            val url = DOWNLOAD_URL
            val target = destDir.resolve(executableName())
            runCatching {
                Files.createDirectories(destDir)
                URI(url).toURL().openStream().use { input ->
                    Files.copy(input, target, REPLACE_EXISTING)
                }
                target.toFile().setExecutable(true)
            }.onFailure { cause ->
                throw IllegalStateException(
                    "自动下载 appimagetool 失败（${cause.message}）。两条出路：\n" +
                        "  1. 手动安装 appimagetool 并放进 PATH；\n" +
                        "  2. 把 krema.bundling.appImageToolAutoDownload 设为 false 关闭自动下载。\n" +
                        "下载地址：$url",
                    cause,
                )
            }
            return target
        }

        /** 持续构建版 appimagetool 的下载地址（随宿主架构选 x86_64）。 */
        private const val DOWNLOAD_URL =
            "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
    }
}
