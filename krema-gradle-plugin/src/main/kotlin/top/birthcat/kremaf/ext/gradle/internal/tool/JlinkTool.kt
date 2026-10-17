/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.tool

import org.gradle.api.GradleException
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.internal.domain.executableSuffix
import java.nio.file.Path

/** jlink 适配：按模块清单裁出最小运行时镜像。 */
internal class JlinkTool(
    private val execOperations: ExecOperations,
) {
    /** 先清空输出目录，jlink 不接受已存在的目录。 */
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
        if (result.exitValue != 0) throw GradleException("Failed to build the runtime image (modules: $modules): \n${result.output}")
    }
}
