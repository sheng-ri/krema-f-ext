/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.tool

import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * 一次 exec 的捕获结果。
 *
 * stdout/stderr 合到同一份流：[output] 按默认字符集解给控制台与解析，[bytes] 原样保留给落盘，
 * 中间不经字符串中转就不会把非 UTF-8 的报告改写坏（jdeps 在中文 Windows 上走 GBK）。
 */
internal class CapturedExec(
    val exitValue: Int,
    stream: ByteArrayOutputStream,
) {
    val output: String = String(stream.toByteArray(), Charset.defaultCharset())
    val bytes: ByteArray = stream.toByteArray()
}

/**
 * exec 一次命令并捕获输出，忽略退出码（成败由调用方按自己的语义判定）。
 *
 * jdeps 之类非零退出仍吐报告、jpackage 之类必须非零即败，失败策略塞不进同一处固定逻辑，故这里只负责「执行 + 捕获」。
 * 默认在当前目录跑；[workingDir] 给抽取类命令用（如 appimagetool 的 `--appimage-extract` 须在工作目录内解包）。
 */
internal fun ExecOperations.capture(
    command: List<String>,
    workingDir: java.nio.file.Path? = null,
): CapturedExec {
    val captured = ByteArrayOutputStream()
    val exit =
        exec { spec ->
            spec.commandLine(command)
            spec.isIgnoreExitValue = true
            spec.standardOutput = captured
            spec.errorOutput = captured
            if (workingDir != null) spec.workingDir = workingDir.toFile()
        }.exitValue
    return CapturedExec(exit, captured)
}
