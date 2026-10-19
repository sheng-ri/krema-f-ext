/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.domain

import top.birthcat.kremaf.ext.gradle.api.Platform
import java.io.File
import java.util.Locale

/** 平台探测：按 JVM 的 `os.name` / `os.arch` 判定，不复用上游实现。 */
internal object PlatformDetector {
    /** 探测宿主平台族。 */
    fun detect() = Platform.of(System.getProperty("os.name"))

    /** 探测当前架构标识，如 `aarch64`、`x86_64`。 */
    fun getArch() = normalizeArch(System.getProperty("os.arch"))

    /**
     * 把 JVM 的架构别名归一为三类。
     *
     * `arm64` / `aarch64`、`amd64` / `x86_64` 是同一架构的两种写法，
     * 不归一会让产物路径随运行 JVM 漂移。
     */
    fun normalizeArch(arch: String) =
        when (arch.lowercase(Locale.ROOT)) {
            "aarch64" -> "aarch64"
            "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            "x86", "i386", "i686" -> "x86"
            else -> arch.lowercase(Locale.ROOT)
        }
}

/** 当前平台的可执行文件后缀。 */
internal fun executableSuffix() = if (File.separatorChar == '\\') ".exe" else ""

/**
 * 把一行命令变成 argv。
 *
 * Windows 必须借道 `cmd /c`：`npm` 实际是 `npm.cmd`，
 * `CreateProcess` 不给无扩展名的命令补 `.cmd`。
 */
internal fun commandLineFor(command: String) = commandLineFor(command, PlatformDetector.detect())

/** 显式给定平台，便于覆盖 Windows 分支的单测。 */
internal fun commandLineFor(
    command: String,
    platform: Platform,
) = command.trim().let { if (platform == Platform.WINDOWS) listOf("cmd", "/c", it) else it.split(Regex("\\s+")) }

/** jpackage app-image 的目录名：macOS 带 `.app`，其余与 `--name` 同名。 */
internal fun Platform.appImageDirName(appName: String) = if (this == Platform.DARWIN) "$appName.app" else appName
