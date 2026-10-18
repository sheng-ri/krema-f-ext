/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.tool

import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.file.Path

/** 一次 exec 的结果；bytes 保留原样，避免按平台字符集改写报告。 */
internal class CapturedExec(
    val exitValue: Int,
    stream: ByteArrayOutputStream,
) {
    val output = String(stream.toByteArray(), Charset.defaultCharset())
    val bytes = stream.toByteArray()
}

/** 执行命令并捕获输出，退出码交给调用方；workingDir 供解包类命令使用。 */
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
