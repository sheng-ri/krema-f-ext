/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import java.nio.file.Files
import java.nio.file.Path

/**
 * 落盘那几件事的唯一实现。
 *
 * 骨架生成、bundler 落启动器、`.env` 读取都要「父目录不存在就先建、再写文本」，收在一处省得各抄一遍。
 */
object FileOps {
    fun writeText(
        path: Path,
        content: String,
    ) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, content)
    }

    fun readText(path: Path): String? = if (Files.isRegularFile(path)) Files.readString(path) else null

    fun markExecutable(path: Path) {
        path.toFile().setExecutable(true, false)
    }
}
