/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import build.krema.core.platform.PlatformDetector
import top.birthcat.kremaf.ext.spi.port.FileSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * 原生 webview 库的定位。
 *
 * 上游 `build.krema.cli.NativeLibraryFinder` 是包私有的，本模块照搬它的搜索语义：
 * 先看 `{platform}/{arch}` 结构化子目录，再退回平铺目录；文件名里含 `webview` 即命中。
 *
 * 平台标识从上游 `build.krema.core.platform.Platform` / `PlatformDetector` 取 —— 这两个类是 Central 公开 API。
 */
class NativeLibraryFinder(
    private val fileSystem: FileSystem,
) {
    /**
     * 在一个基准目录下找库目录。
     *
     * @param baseDir 基准目录，通常是 classpath 里 jar 所在目录或其父目录
     * @return 含原生库的目录，找不到时返回 null
     */
    fun findIn(baseDir: Path): Path? {
        if (!Files.isDirectory(baseDir)) return null
        val archDir = baseDir.resolve(targetSubdir())
        if (Files.isDirectory(archDir) && containsLibrary(archDir)) return archDir
        return baseDir.takeIf(::containsLibrary)
    }

    /**
     * 在候选目录里找原生库文件本身。
     *
     * @param candidates 候选目录，按顺序探测
     * @return 库文件路径，找不到时返回 null
     */
    fun findLibrary(candidates: List<Path>): Path? {
        val name = PlatformDetector.detect().formatLibraryName(LIBRARY_BASE_NAME)
        for (base in candidates) {
            base
                .resolve(targetSubdir())
                .resolve(name)
                .takeIf(Files::exists)
                ?.let { return it }
            base.resolve(name).takeIf(Files::exists)?.let { return it }
        }
        return null
    }

    private fun containsLibrary(dir: Path): Boolean = fileSystem.list(dir).any { it.fileName.toString().contains(LIBRARY_BASE_NAME) }

    private fun targetSubdir(): String = "${PlatformDetector.detect().name.lowercase()}/${PlatformDetector.getArch()}"

    private companion object {
        private const val LIBRARY_BASE_NAME = "webview"
    }
}
