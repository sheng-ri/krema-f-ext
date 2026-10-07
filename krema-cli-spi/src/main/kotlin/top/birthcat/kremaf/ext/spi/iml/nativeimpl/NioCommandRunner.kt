/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.nativeimpl

import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.waitForOrKill
import java.nio.file.Files
import java.nio.file.Path

/**
 * `ProcessBuilder` 实现，给非 Gradle 场景兜底（纯 JUnit 测试、将来的 Maven 插件）。
 *
 * Windows 上 `npm` / `pnpm` / `mvn` 都是 `.cmd` 包装，`ProcessBuilder` 解析不了，所以统一走 `cmd /c` ——
 * 与上游 `ShellCommand` 的处理一致。
 */
class NioCommandRunner : CommandRunner {
    override fun exec(
        command: List<String>,
        workingDir: Path,
        env: Map<String, String>,
    ): Int {
        val process = newProcess(command, workingDir, env)
        return process.waitForOrKill(EXEC_TIMEOUT_MILLIS) ?: -1
    }

    override fun spawn(
        command: List<String>,
        workingDir: Path,
        env: Map<String, String>,
    ): Process = newProcess(command, workingDir, env)

    override fun which(name: String): Path? {
        val probe = if (isWindows()) listOf("where", name) else listOf("which", name)
        val process = newProcess(probe, Path.of("."), emptyMap())
        val line = process.inputStream.bufferedReader().use { it.readLine() }
        if (process.waitForOrKill(PROBE_TIMEOUT_MILLIS) != 0 || line.isNullOrBlank()) return null
        return Path.of(line.trim()).takeIf(Files::exists)
    }

    private fun newProcess(
        command: List<String>,
        workingDir: Path,
        env: Map<String, String>,
    ): Process {
        val builder = ProcessBuilder(wrap(command))
        builder.directory(workingDir.toFile())
        builder.redirectErrorStream(false)
        builder.environment().putAll(env)
        return builder.start()
    }

    private fun wrap(command: List<String>): List<String> = if (isWindows()) listOf("cmd", "/c") + command else command

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    private companion object {
        private const val EXEC_TIMEOUT_MILLIS = 10 * 60 * 1000L
        private const val PROBE_TIMEOUT_MILLIS = 10_000L
    }
}
