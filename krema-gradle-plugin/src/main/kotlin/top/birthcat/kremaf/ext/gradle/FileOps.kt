/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

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

    /**
     * 把 `src` 摊进 `dest`：文件直接拷进 `dest`，目录则把其内容拷进 `dest`。
     * 用来把 classpath 与 dist 收进 bundle 内的目标目录，目录同名不冲突。
     */
    fun copyTree(
        src: Path,
        dest: Path,
    ) {
        if (Files.isDirectory(src)) {
            Files.createDirectories(dest)
            Files.walk(src).use { stream ->
                for (from in stream) {
                    if (from == src) continue
                    val to = dest.resolve(src.relativize(from))
                    if (Files.isDirectory(from)) {
                        Files.createDirectories(to)
                    } else {
                        Files.createDirectories(to.parent)
                        Files.copy(from, to, REPLACE_EXISTING)
                    }
                }
            }
        } else {
            Files.createDirectories(dest)
            Files.copy(src, dest.resolve(src.fileName), REPLACE_EXISTING)
        }
    }
}
