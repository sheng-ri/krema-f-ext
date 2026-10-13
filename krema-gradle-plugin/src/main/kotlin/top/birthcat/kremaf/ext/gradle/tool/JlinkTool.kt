/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.tool

import org.gradle.api.GradleException
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.domain.executableSuffix
import java.nio.file.Path

/** jlink 适配：按模块清单裁出最小运行时镜像，供 jpackage 的 `--runtime-image`。 */
class JlinkTool(
    private val execOperations: ExecOperations,
) {
    /**
     * 裁出运行时镜像到 [outputDir]。
     *
     * 目标目录先清空再裁：jlink 见到已存在的目录就报「目录已存在」，而该目录是本 task 声明的产物，重建前清空安全。
     */
    @Throws(GradleException::class)
    fun carve(
        binDir: Path,
        modules: String,
        outputDir: Path,
    ) {
        outputDir.toFile().deleteRecursively()
        val command =
            listOf(
                binDir.resolve("jlink" + executableSuffix()).toString(),
                "--add-modules",
                modules,
                "--output",
                outputDir.toAbsolutePath().toString(),
            )
        val result = execOperations.capture(command)
        if (result.exitValue != 0) throw GradleException("裁剪运行时镜像失败（模块：$modules）：\n${result.output}")
    }
}
