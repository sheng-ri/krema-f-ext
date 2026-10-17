/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.tool

import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.file.Path

/** 一次 exec 的捕获结果：[output] 按默认字符集解给控制台、[bytes] 原样保留给落盘，不经字符串中转以免改写坏非 UTF-8 报告。 */
internal class CapturedExec(
    val exitValue: Int,
    stream: ByteArrayOutputStream,
) {
    val output = String(stream.toByteArray(), Charset.defaultCharset())
    val bytes = stream.toByteArray()
}

/** exec 一次命令并捕获输出、忽略退出码；成败语义交由调用方判定。[workingDir] 供抽取类命令使用。 */
internal fun ExecOperations.capture(
    command: List<String>,
    workingDir: Path? = null,
): CapturedExec {
    val captured = ByteArrayOutputStream()
    val exit =
        exec {
            commandLine(command)
            isIgnoreExitValue = true
            standardOutput = captured
            errorOutput = captured
            if (workingDir != null) {
                this.workingDir = workingDir.toFile()
            }
        }.exitValue
    return CapturedExec(exit, captured)
}
