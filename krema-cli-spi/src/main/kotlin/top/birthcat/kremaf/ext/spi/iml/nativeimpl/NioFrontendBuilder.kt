/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.nativeimpl

import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.FrontendBuilder
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.file.Path

/**
 * 前端构建与 dev server。
 *
 * 上游把子进程直接 `inheritIO` 挂在终端上，命令检测还看锁文件。这里拆成两件事：
 * 命令拼装是纯函数，执行走 [CommandRunner]（Gradle 侧换成 `ExecOperations`）。
 */
class NioFrontendBuilder(
    private val commandRunner: CommandRunner,
) : FrontendBuilder {
    override fun build(
        project: KremaProject,
        env: Map<String, String>,
    ): Path {
        val frontend = project.frontend
        val workingDir = frontend.workingDir ?: project.dir
        val exit = commandRunner.exec(split(frontend.buildCommand), workingDir, env)
        check(exit == 0) { "前端构建失败，退出码 $exit" }
        return workingDir.resolve(project.config.build.outDir)
    }

    override fun startDevServer(
        project: KremaProject,
        env: Map<String, String>,
    ): Process {
        val frontend = project.frontend
        val workingDir = frontend.workingDir ?: project.dir
        return commandRunner.spawn(split(frontend.devCommand), workingDir, env)
    }

    override fun isDevServerUp(url: String): Boolean {
        val port = runCatching { URI(url).port }.getOrElse { return false }
        if (port == -1) return isPortOpen(DEV_URL_HOST, DEFAULT_PORT)
        return isPortOpen(DEV_URL_HOST, port) || isPortOpen(IPV6_LOOPBACK, port)
    }

    private fun isPortOpen(
        host: String,
        port: Int,
    ): Boolean = runCatching { Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS) } }.isSuccess

    private fun split(command: String): List<String> = command.trim().split(WHITESPACE).filter { it.isNotEmpty() }

    private companion object {
        private const val DEV_URL_HOST = "127.0.0.1"
        private const val IPV6_LOOPBACK = "::1"
        private const val DEFAULT_PORT = 80
        private const val CONNECT_TIMEOUT_MILLIS = 500
        private val WHITESPACE = Regex("\\s+")
    }
}
