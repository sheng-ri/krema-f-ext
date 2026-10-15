/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.tool

import org.gradle.api.file.FileCollection
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.domain.executableSuffix
import java.nio.file.Path

fun jdepsModulesFrom(text: String): String =
    text
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

/** 一次 jdeps 调用结果：[text] 解给控制台/解析、[bytes] 原样落盘，避开平台默认字符集（中文 Windows 走 GBK）改写坏报告。 */
internal class JdepsOutput(
    val exitValue: Int,
    val text: String,
    val bytes: ByteArray,
) {
    /** jdeps 成功时把模块清单打在第一行，取第一行非空。 */
    fun firstLine(): String = jdepsModulesFrom(text)
}

/** 调 jdeps 推导模块清单供运行时镜像与检视 task 复用；[run] 只执行捕获、绝不抛，失败语义交调用方。 */
internal class JdepsRunner(
    private val execOperations: ExecOperations,
    private val binDir: Path,
    private val multiRelease: String,
    private val classpathFiles: FileCollection,
) {
    fun run(verbose: Boolean): JdepsOutput {
        // jdeps 只认路径形式的输入：传 -cp 再给类名会报「路径不存在」并失败。
        val paths = classpathFiles.files.filter { it.exists() }.map { it.absolutePath }
        val command =
            listOf(
                binDir.resolve("jdeps" + executableSuffix()).toString(),
                // -summary 或 -verbose 与 --print-module-deps 互斥（jdeps 直接报错退出），
                // 所以两种检视 task 各走一个开关，而不是同一种 task 上切参数。
                if (verbose) "-v" else "--print-module-deps",
                // --ignore-missing-deps 抑制不了 -v 下「Module xxx not found」那类退出码，
                // 故 verbose 那条路径对非零退出照常出报告。
                "--ignore-missing-deps",
                // --multi-release 必须给，且必须与 toolchain 版本一致：jackson 这类依赖是多发行版 jar，
                // 不给这个选项 jdeps 直接拒读，模块推导就废了。
                "--multi-release",
                multiRelease,
            ) + paths
        val result = execOperations.capture(command)
        return JdepsOutput(result.exitValue, result.output, result.bytes)
    }
}
