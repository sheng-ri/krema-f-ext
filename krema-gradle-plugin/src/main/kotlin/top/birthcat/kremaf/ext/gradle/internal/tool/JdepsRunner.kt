/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.tool

import org.gradle.api.file.FileCollection
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.internal.domain.executableSuffix
import java.nio.file.Path

/** 取首个非空行，即 jdeps 输出的模块清单。 */
internal fun jdepsModulesFrom(text: String) =
    text
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

/** jdeps 输出：text 给控制台，bytes 原样落盘以免字符集改写。 */
internal class JdepsOutput(
    val exitValue: Int,
    val text: String,
    val bytes: ByteArray,
) {
    /** 模块清单在第一行。 */
    fun firstLine() = jdepsModulesFrom(text)
}

/** 调 jdeps 推导模块；run 只捕获，成败由调用方判定。 */
internal class JdepsRunner(
    private val execOperations: ExecOperations,
    private val binDir: Path,
    private val multiRelease: String,
    private val classpathFiles: FileCollection,
) {
    /** 执行一次 jdeps；verbose 与 --print-module-deps 互斥。 */
    fun run(verbose: Boolean): JdepsOutput {
        // jdeps 只认路径输入，-cp 加类名会报路径不存在。
        val paths = classpathFiles.files.filter { it.exists() }.map { it.absolutePath }
        val command =
            listOf(
                binDir.resolve("jdeps" + executableSuffix()).toString(),
                if (verbose) "-v" else "--print-module-deps",
                "--ignore-missing-deps",
                // 必须给且与目标字节码版本一致，否则多发行版 jar 直接拒读。
                "--multi-release",
                multiRelease,
            ) + paths
        val result = execOperations.capture(command)
        return JdepsOutput(result.exitValue, result.output, result.bytes)
    }
}
