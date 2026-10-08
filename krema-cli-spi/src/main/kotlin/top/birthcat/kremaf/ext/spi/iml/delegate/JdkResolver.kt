/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.waitForOrKill
import java.nio.file.Files
import java.nio.file.Path

/**
 * JDK 25 的定位。
 *
 * 自研：六档搜索顺序对齐上游 `krema-cli` 的 `JdkResolver`（该模块不发制品），但所有子进程探测转由
 * [CommandRunner] 执行 —— 测试里换掉 runner，就不需要机器上真有 JDK 25。
 *
 * `KREMA_JAVA_HOME` 不做版本校验（只判可执行），`JAVA_HOME` 起才探测版本。
 */
class JdkResolver(
    private val commandRunner: CommandRunner,
) {
    /**
     * 找 Java 25。
     *
     * @param env 环境变量快照，测试可注入
     * @param userHome 用户主目录，测试可注入
     * @return `JAVA_HOME` 目录，找不到时返回 null
     */
    fun resolve(
        env: Map<String, String> = System.getenv(),
        userHome: Path = Path.of(System.getProperty("user.home")),
    ): Path? {
        fromEnv(env["KREMA_JAVA_HOME"])?.let { return it }
        fromJavaHomeEnv(env["JAVA_HOME"])?.let { return it }
        if (isMacOS()) fromMacOSJavaHome()?.let { return it }
        if (isLinux()) fromLinuxAlternatives()?.let { return it }
        fromPathJava()?.let { return it }
        fromTemurinInstall(userHome)?.let { return it }
        return fromCurrentJvm()
    }

    /**
     * 找 `jpackage`。
     *
     * 先认 [resolve] 给的 JDK 25，再退到 PATH：CI 镜像里 `jpackage` 与 `java` 同源，
     * 但容器可能只把 `jpackage` 挂进 PATH。
     *
     * @return `jpackage` 所在目录，找不到时返回 null
     */
    fun resolveJpackage(
        env: Map<String, String> = System.getenv(),
        userHome: Path = Path.of(System.getProperty("user.home")),
    ): Path? {
        val name = if (isWindows()) "jpackage.exe" else "jpackage"
        resolve(env, userHome)
            ?.resolve("bin")
            ?.takeIf { Files.isExecutable(it.resolve(name)) }
            ?.let { return it }
        return commandRunner.which("jpackage")?.parent
    }

    private fun fromEnv(value: String?): Path? = value?.takeIf { it.isNotBlank() }?.let { Path.of(it) }?.takeIf(::hasJavaBinary)

    private fun fromJavaHomeEnv(value: String?): Path? =
        value?.takeIf { it.isNotBlank() }?.let { Path.of(it) }?.takeIf {
            hasJavaBinary(it) && isJava25(it.resolve(javaBinaryName()))
        }

    private fun fromMacOSJavaHome(): Path? {
        val process = commandRunner.spawn(listOf("/usr/libexec/java_home", "-v", REQUIRED_VERSION), Path.of("."))
        val line: String? = process.inputStream.bufferedReader().use { it.readLine() }
        val exit = process.waitForOrKill(PROBE_TIMEOUT_MILLIS)
        if (exit != 0 || line.isNullOrBlank()) return null
        return Path.of(line.trim()).takeIf(::hasJavaBinary)
    }

    private fun fromLinuxAlternatives(): Path? {
        val alternatives = commandRunner.which("update-alternatives") ?: return null
        val query = readProcessOutput(listOf(alternatives.toString(), "--query", "java")) ?: return null
        val javaPath =
            query
                .lineSequence()
                .firstOrNull { it.startsWith("Value:") }
                ?.removePrefix("Value:")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
        val javaBin = Path.of(javaPath)
        val home = javaBin.parent?.parent ?: return null
        return home.takeIf { hasJavaBinary(it) && isJava25(javaBin) }
    }

    private fun fromPathJava(): Path? {
        val java = commandRunner.which("java") ?: return null
        val real = runCatching { java.toRealPath() }.getOrElse { java }
        if (!isJava25(real)) return null
        real.parent
            ?.parent
            ?.takeIf(::hasJavaBinary)
            ?.let { return it }
        return queryJavaHome(real)?.takeIf(::hasJavaBinary)
    }

    private fun fromTemurinInstall(userHome: Path): Path? {
        val dir = userHome.resolve(".krema").resolve("jdk").resolve("temurin-$REQUIRED_VERSION")
        if (hasJavaBinary(dir)) return dir
        return dir.resolve("Contents/Home").takeIf(::hasJavaBinary)
    }

    private fun fromCurrentJvm(): Path? =
        System.getProperty("java.home")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }?.takeIf {
            hasJavaBinary(it) && isJava25(it.resolve(javaBinaryName()))
        }

    private fun queryJavaHome(javaBin: Path): Path? =
        readProcessOutput(listOf(javaBin.toString(), "-XshowSettings:properties", "-version"))
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("java.home") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { Path.of(it) }

    private fun isJava25(javaBin: Path): Boolean =
        readProcessOutput(listOf(javaBin.toString(), "-version"))
            ?.let { VERSION_PATTERN.find(it)?.groupValues?.get(1) == REQUIRED_VERSION } == true

    private fun readProcessOutput(command: List<String>): String? {
        val process = commandRunner.spawn(command, Path.of("."))
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return if (process.waitForOrKill(PROBE_TIMEOUT_MILLIS) == 0) output else null
    }

    private fun hasJavaBinary(javaHome: Path): Boolean = Files.isExecutable(javaHome.resolve(javaBinaryName()))

    private fun javaBinaryName(): String = if (isWindows()) "bin/java.exe" else "bin/java"

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    private fun isMacOS(): Boolean = System.getProperty("os.name").lowercase().let { it.contains("mac") || it.contains("darwin") }

    private fun isLinux(): Boolean =
        System.getProperty("os.name").lowercase().let { it.contains("nux") || it.contains("nix") || it.contains("bsd") }

    private companion object {
        private const val REQUIRED_VERSION = "25"
        private const val PROBE_TIMEOUT_MILLIS = 10_000L
        private val VERSION_PATTERN = Regex("\"(\\d+)")
    }
}
