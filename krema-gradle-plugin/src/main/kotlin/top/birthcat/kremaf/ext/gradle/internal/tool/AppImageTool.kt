/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.tool

import org.gradle.api.GradleException
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.api.BundleSettings
import top.birthcat.kremaf.ext.gradle.api.Platform
import top.birthcat.kremaf.ext.gradle.internal.domain.PlatformDetector
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** 封装 appimagetool：定位或下载工具，把 app-image 打成 .AppImage。 */
internal class AppImageTool(
    private val execOperations: ExecOperations,
) {
    /** 打包到 [outputDir] 并返回产物；缺工具且禁用下载时报错。 */
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
                        "appimagetool was not found on PATH and krema.bundling.appImageToolAutoDownload is false. " +
                            "Install appimagetool and put it on PATH, or enable that switch.",
                    )
                }
        // app-image 是 kremaBundle 的产物，不能改写；复制到 staging 再补元信息。
        val staging = outputDir.resolve(APPIMAGE_STAGING_DIR)
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
        val tool = resolveAppImageTool(rawTool, outputDir)
        val out = outputDir.resolve("${settings.appName}.AppImage")
        runCommand(
            listOf(tool.toAbsolutePath().toString(), staging.toString(), out.toAbsolutePath().toString()),
            "appimagetool failed to package the AppImage",
        )
        // staging 只是输出区里的临时副本，打包完即删。
        staging.toFile().deleteRecursively()
        return out
    }

    /** jpackage 的 app-image 不带 .desktop，补到 staging 副本。 */
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

    /** 下载版工具需 FUSE；抽出内部 ELF 再打包，PATH 上装的真二进制直接返回。 */
    private fun resolveAppImageTool(
        rawTool: Path,
        outputDir: Path,
    ): Path {
        val extractDir = outputDir.resolveSibling(APPIMAGE_EXTRACT_DIR)
        val inner = extractDir.resolve(APPIMAGE_INNER_PATH)
        if (Files.isRegularFile(inner)) return inner
        Files.createDirectories(extractDir)
        // --appimage-extract 只解包、不挂载，故无 FUSE 也能跑。
        val result =
            execOperations.capture(
                listOf(rawTool.toAbsolutePath().toString(), "--appimage-extract"),
                workingDir = extractDir,
            )
        if (result.exitValue != 0) return rawTool
        if (!Files.isRegularFile(inner)) {
            throw GradleException("Failed to extract appimagetool: \n${result.output}")
        }
        return inner
    }

    private fun runCommand(
        command: List<String>,
        failure: String,
    ) {
        val result = execOperations.capture(command)
        if (result.exitValue != 0) throw GradleException("$failure: \n${result.output}")
    }

    companion object {
        /** 当前平台的工具可执行文件名。 */
        private fun executableName() = if (PlatformDetector.detect() == Platform.WINDOWS) "appimagetool.exe" else "appimagetool"

        /** 在给定目录里找工具；纯函数便于单测。 */
        internal fun findInPaths(paths: List<Path>): Path? {
            val name = executableName()
            return paths
                .firstOrNull { Files.isRegularFile(Path.of(it.toString(), name)) }
                ?.resolve(name)
        }

        /** 在 PATH 里找工具；找不到返回 null。 */
        internal fun findInPath(): Path? {
            val path = System.getenv("PATH") ?: return null
            return findInPaths(path.split(File.pathSeparator).map { Path.of(it) })
        }

        /** 下载到 [destDir]；失败给出「手动装 / 关开关」两条出路。 */
        @Throws(IllegalStateException::class)
        internal fun download(destDir: Path): Path {
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
                    "Failed to download appimagetool (${cause.message}). Two options:\n" +
                        "  1. Install appimagetool manually and put it on PATH;\n" +
                        "  2. Set krema.bundling.appImageToolAutoDownload to false to disable automatic download.\n" +
                        "Download URL: $url",
                    cause,
                )
            }
            return target
        }

        private const val APPIMAGE_STAGING_DIR = "appimage-staging"
        private const val APPIMAGE_EXTRACT_DIR = ".appimagetool-extract"
        private const val APPIMAGE_INNER_PATH = "squashfs-root/usr/bin/appimagetool"

        /** 持续构建版下载地址；目前只有 x86_64。 */
        private const val DOWNLOAD_URL =
            "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
    }
}
