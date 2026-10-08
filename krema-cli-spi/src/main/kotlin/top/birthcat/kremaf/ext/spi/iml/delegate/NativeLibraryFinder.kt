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
 * 命中判据是「动态库后缀」而不是某个具体名字：目录里可能躺着好几个原生库，只认 `webview` 会漏。
 * 一个目录里的命中全部返回，挑哪个由调用方定。
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
     * @return 第一个命中的库文件路径，找不到时返回 null
     */
    fun findLibrary(candidates: List<Path>): Path? = findLibraries(candidates).firstOrNull()

    /**
     * 在候选目录里找全部原生库文件。
     *
     * 与 [findLibrary] 同一套搜索顺序，区别只是命中了不回头 —— 一个目录里可能有好几个库
     * （webview 只是其中一个），所以目录里凡是平台形态的动态库都算命中，不挑名字。
     *
     * @param candidates 候选目录，按顺序探测
     * @return 命中的库文件，按候选顺序排列，可能为空
     */
    fun findLibraries(candidates: List<Path>): List<Path> =
        candidates.flatMap { base ->
            listOf(base.resolve(targetSubdir()), base).flatMap(::librariesIn)
        }

    private fun librariesIn(dir: Path): List<Path> = fileSystem.list(dir).filter(::isNativeLibrary)

    /** 判据是文件名形态（`lib*.so` / `*.dll`），不是具体某个库 —— 目录里可能有多个。 */
    private fun isNativeLibrary(file: Path): Boolean = file.fileName.toString().endsWith(PlatformDetector.detect().librarySuffix)

    private fun containsLibrary(dir: Path): Boolean = librariesIn(dir).isNotEmpty()

    private fun isLibraryFile(file: Path): Boolean = file.parent?.let(::containsLibrary) == true

    private fun targetSubdir(): String = "${PlatformDetector.detect().name.lowercase()}/${PlatformDetector.getArch()}"
}
