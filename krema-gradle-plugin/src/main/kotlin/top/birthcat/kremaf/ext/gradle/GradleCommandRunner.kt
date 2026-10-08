/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.spi.port.CommandRunner
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.inject.Inject

/**
 * [CommandRunner] 的 Gradle 实现。
 *
 * 子进程走 `ExecOperations` 而不是 `ProcessBuilder`：Gradle 借此把子进程纳入自己的生命周期
 * （构建被取消时一并收尾），且这块 API 对 Configuration Cache 友好。
 *
 * 经 `services` 注入，不碰 `Project` —— Gradle 9.8 的 task action 约束。
 */
class GradleCommandRunner
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : CommandRunner {
        override fun exec(
            command: List<String>,
            workingDir: Path,
            env: Map<String, String>,
        ): Int {
            val result =
                execOperations.exec { spec ->
                    spec.commandLine(command)
                    spec.workingDir(workingDir.toFile())
                    spec.environment(env)
                    spec.isIgnoreExitValue = true
                }
            return result.exitValue
        }

        override fun spawn(
            command: List<String>,
            workingDir: Path,
            env: Map<String, String>,
        ): Process {
            val builder = ProcessBuilder(command)
            builder.directory(workingDir.toFile())
            builder.environment().putAll(env)
            return builder.start()
        }

        override fun which(name: String): Path? {
            val probe = if (isWindows()) listOf("where", name) else listOf("which", name)
            val output = ByteArrayOutputStream()
            val result =
                execOperations.exec { spec ->
                    spec.commandLine(probe)
                    spec.standardOutput = output
                    spec.isIgnoreExitValue = true
                }
            if (result.exitValue != 0) return null
            val line = output.toString().lineSequence().firstOrNull { it.isNotBlank() } ?: return null
            return Path.of(line.trim()).takeIf { it.toFile().exists() }
        }

        private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
    }
