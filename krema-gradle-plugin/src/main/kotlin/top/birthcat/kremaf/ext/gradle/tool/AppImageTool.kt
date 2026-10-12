/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.tool

import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.PlatformDetector
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * 定位（必要时下载）AppImage 的封装工具 `appimagetool`。
 *
 * 只管「工具在哪、没有就下」，不碰打包流程：封装动作在 [KremaPlatformTask] 里。PATH 优先，
 * 找不到且开了自动下载才联网拉一份到构建目录 —— 这样常驻 PATH 的版本永远被用上，CI 才可控。
 */
object AppImageTool {
    /** 当前平台的工具可执行文件名。 */
    private fun executableName(): String = if (PlatformDetector.detect() == Platform.WINDOWS) "appimagetool.exe" else "appimagetool"

    /**
     * 在给定目录列表里找 `appimagetool`。
     *
     * 抽成纯函数（不读环境变量）便于单测：调用方把 `PATH` 拆好传进来即可。
     *
     * @return 第一个存在的可执行文件路径，都没有返回 null
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
     *
     * @throws IllegalStateException 下载失败，报错里带「手动装」与「关开关」两条出路
     */
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
