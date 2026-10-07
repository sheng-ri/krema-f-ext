/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.nativeimpl

import top.birthcat.kremaf.ext.spi.port.FileSystem
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * `java.nio` 实现。
 *
 * 这是"为什么必须自己写"的第一处：上游没有可复用的文件树工具类
 * （`BuildCommand.copyAssets` 把 `walkFileTree` 写死在方法体里），而三个命令都要复制整棵树，
 * 与其抄三遍，不如收成一个 port。
 */
class NioFileSystem : FileSystem {
    override fun copyTree(
        source: Path,
        target: Path,
    ) {
        if (!Files.exists(source)) return
        Files.walkFileTree(
            source,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    Files.createDirectories(target.resolve(source.relativize(dir)))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    override fun copyMatching(
        source: Path,
        target: Path,
        suffixes: List<String>,
    ): List<Path> {
        if (!Files.isDirectory(source)) return emptyList()
        Files.createDirectories(target)
        val copies = mutableListOf<Path>()
        Files.list(source).use { stream ->
            val matches =
                stream
                    .filter { entry -> Files.isRegularFile(entry) && suffixes.any { entry.fileName.toString().endsWith(it) } }
                    .toList()
            for (entry in matches) {
                val destination = target.resolve(entry.fileName)
                Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING)
                copies.add(destination)
            }
        }
        return copies
    }

    override fun copyFlat(
        source: Path,
        targetDir: Path,
    ) {
        Files.createDirectories(targetDir)
        Files.copy(source, targetDir.resolve(source.fileName), StandardCopyOption.REPLACE_EXISTING)
    }

    override fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    dir: Path,
                    exc: IOException?,
                ): FileVisitResult {
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    override fun writeText(
        path: Path,
        content: String,
    ) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, content)
    }

    override fun readText(path: Path): String? = if (Files.isRegularFile(path)) Files.readString(path) else null

    override fun list(path: Path): List<Path> = if (Files.isDirectory(path)) Files.list(path).use { it.toList() } else emptyList()

    override fun markExecutable(path: Path) {
        path.toFile().setExecutable(true, false)
    }
}
