/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.file.FileCollection
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.executableSuffix
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.file.Path

/**
 * 一次 jdeps 调用的结果。
 *
 * [bytes] 与 [text] 分开给：jdeps 走平台默认字符集（中文 Windows 上是 GBK），
 * [text] 解给控制台和解析用，[bytes] 原样落盘用，中间不经字符串中转就不会把非 UTF-8 的报告改写坏。
 */
internal class JdepsOutput(
    val exitValue: Int,
    val text: String,
    val bytes: ByteArray,
) {
    /** jdeps 成功时把模块清单打在第一行，取第一行非空。 */
    fun firstLine(): String =
        text
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
}

/**
 * 调 jdeps 推导模块清单，供运行时镜像与两张 jdeps 检视 task 复用。
 *
 * 失败语义交给调用方：[run] 只执行并捕获，绝不抛 —— `jdeps -v` 在部分 jar 组合下会一边 exit != 0
 * 一边照常吐出可用的报告，而 `--print-module-deps` 推导不出来时又必须硬失败。两种诉求塞不进一个
 * 固定策略里。
 */
internal class JdepsRunner(
    private val execOperations: ExecOperations,
    private val binDir: Path,
    private val multiRelease: String,
    private val classpathFiles: FileCollection,
) {
    fun run(verbose: Boolean): JdepsOutput {
        // jdeps 只认路径形式的输入：传 -cp 再给类名会报「路径不存在」并失败。
        val paths = classpathFiles.files.filter { it.exists() }.map { it.absolutePath }
        val captured = ByteArrayOutputStream()
        val exit =
            execOperations
                .exec { spec ->
                    spec.commandLine(
                        listOf(
                            binDir.resolve("jdeps" + executableSuffix()).toString(),
                            // -summary 或 -verbose 与 --print-module-deps 互斥（jdeps 直接报错退出），
                            // 所以两张检视 task 各走一个开关，而不是同一张 task 上切参数。
                            if (verbose) "-v" else "--print-module-deps",
                            // --ignore-missing-deps 抑制不了 -v 下「Module xxx not found」那类退出码，
                            // 故 verbose 那条路径对非零退出照常出报告。
                            "--ignore-missing-deps",
                            // --multi-release 必须给，且必须与 toolchain 版本一致：jackson 这类依赖是
                            // 多发行版 jar，不给这个选项 jdeps 直接拒读，模块推导就废了。
                            "--multi-release",
                            multiRelease,
                        ) + paths,
                    )
                    spec.isIgnoreExitValue = true
                    spec.standardOutput = captured
                    spec.errorOutput = captured
                }.exitValue
        val bytes = captured.toByteArray()
        return JdepsOutput(exit, String(bytes, Charset.defaultCharset()), bytes)
    }
}
