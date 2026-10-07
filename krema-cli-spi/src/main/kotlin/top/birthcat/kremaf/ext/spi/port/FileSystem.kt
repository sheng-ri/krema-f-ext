/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * 文件系统访问口。
 *
 * 编排层不直接 `Files.*`，一来测试可以塞一个内存实现，二来「复制整棵树」这类动作在 Windows 上的坑
 * 集中在一处好修。
 */
interface FileSystem {
    /** 递归复制目录，目标已存在时覆盖。 */
    fun copyTree(
        source: Path,
        target: Path,
    )

    /**
     * 复制目录下匹配后缀的文件（不递归）。
     *
     * @param suffixes 后缀白名单，命中任一即复制
     * @return 实际复制的文件列表
     */
    fun copyMatching(
        source: Path,
        target: Path,
        suffixes: List<String>,
    ): List<Path>

    /** 复制单个文件到目录（保持文件名），目标目录不存在时自动创建。 */
    fun copyFlat(
        source: Path,
        targetDir: Path,
    )

    /** 递归删除，路径不存在时静默返回。 */
    fun deleteTree(path: Path)

    /** 写文本文件，父目录不存在时自动创建。 */
    fun writeText(
        path: Path,
        content: String,
    )

    /** 读文本文件，不存在时返回 null。 */
    fun readText(path: Path): String?

    /** 列出目录下的直接子项，目录不存在时返回空列表。 */
    fun list(path: Path): List<Path>

    /** 给文件加可执行位。 */
    fun markExecutable(path: Path)
}
