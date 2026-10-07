/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import top.birthcat.kremaf.ext.spi.port.FileSystem
import java.nio.file.Path

/**
 * 原生 webview 库的定位。
 *
 * 自研：搜索语义对齐上游 `krema-cli` 的 `NativeLibraryFinder`（该模块不发制品），平台探测由本模块的
 * [PlatformDetector] 提供，不再引用上游 `PlatformDetector`。
 *
 * 搜索顺序：先看 `{platform}/{arch}` 结构化子目录，再退回平铺目录。
 * 命中判据是库文件名等于当前平台的 `formatLibraryName("webview")`，子串匹配会漏 `libkrema_webview.so` 这类命名。
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
        val archDir = baseDir.resolve(targetSubdir())
        if (containsLibrary(archDir)) return archDir
        return baseDir.takeIf(::containsLibrary)
    }

    /**
     * 在候选目录里找原生库文件本身。
     *
     * @param candidates 候选目录，按顺序探测
     * @return 库文件路径，找不到时返回 null
     */
    fun findLibrary(candidates: List<Path>): Path? {
        val name = libraryFileName()
        for (base in candidates) {
            base
                .resolve(targetSubdir())
                .resolve(name)
                .takeIf(::isLibraryFile)
                ?.let { return it }
            base.resolve(name).takeIf(::isLibraryFile)?.let { return it }
        }
        return null
    }

    private fun containsLibrary(dir: Path): Boolean = fileSystem.list(dir).any { it.fileName.toString() == libraryFileName() }

    private fun isLibraryFile(file: Path): Boolean = file.parent?.let(::containsLibrary) == true

    private fun libraryFileName(): String = PlatformDetector.formatLibraryName(LIBRARY_BASE_NAME)

    private fun targetSubdir(): String = "${PlatformDetector.detect().name.lowercase()}/${PlatformDetector.getArch()}"

    private companion object {
        private const val LIBRARY_BASE_NAME = "webview"
    }
}
