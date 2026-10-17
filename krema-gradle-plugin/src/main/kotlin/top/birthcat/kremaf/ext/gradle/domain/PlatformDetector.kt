/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.domain

import java.io.File
import java.util.Locale

/**
 * 平台探测。
 *
 * 自研：上游的 `build.krema.core.platform.PlatformDetector` 不再复用（编排能力全自研，不依赖上游制品）。
 * 探测口径按 JVM 的 `os.name` / `os.arch`，覆盖 Krema 支持的三个平台族。
 */
object PlatformDetector {
    fun detect() = Platform.of(System.getProperty("os.name"))

    /** 探测当前架构标识，如 `aarch64`、`x86_64`。 */
    fun getArch() = normalizeArch(System.getProperty("os.arch"))

    fun normalizeArch(arch: String) =
        when (arch.lowercase(Locale.ROOT)) {
            "aarch64" -> "aarch64"
            "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            "x86", "i386", "i686" -> "x86"
            else -> arch.lowercase(Locale.ROOT)
        }
}

/** Krema 支持的平台族。 */
enum class Platform {
    DARWIN,
    LINUX,
    WINDOWS,
    UNKNOWN,
    ;

    companion object {
        fun of(osName: String): Platform {
            val name = osName.lowercase(Locale.ROOT)
            return when {
                "mac" in name || "darwin" in name -> DARWIN
                "win" in name -> WINDOWS
                "nux" in name || "nix" in name || "bsd" in name -> LINUX
                else -> UNKNOWN
            }
        }
    }
}

/** 当前平台的可执行文件后缀，Windows 上为 `.exe`。 */
internal fun executableSuffix() = if (File.separatorChar == '\\') ".exe" else ""

/**
 * 把用户写的一行命令变成 `ProcessBuilder` / `ExecOperations` 用的 argv。
 *
 * Windows 上必须借道 `cmd /c`：Node 系工具装出来的入口是 `npm.cmd` / `npm.ps1`，
 * 而 `CreateProcess` 只给无扩展名的命令补 `.exe`，直接起 `npm` 会报「系统找不到指定的文件」。
 * 走 `cmd` 顺带把 `&&`、管道这些 shell 语法兑现给用户 —— 配置项本来的语义就是「一整行命令」。
 * 其余平台不套 shell：少一层进程，退出码与信号直达。
 */
internal fun commandLineFor(command: String) = commandLineFor(command, PlatformDetector.detect())

/**
 * 把一行命令变成 argv，显式给定平台（便于单测 Windows 借道分支）。
 *
 * Windows 上借道 `cmd /c`：见 [commandLineFor] 的 why。
 */
internal fun commandLineFor(
    command: String,
    platform: Platform,
): List<String> {
    val trimmed = command.trim()
    return if (platform == Platform.WINDOWS) {
        listOf("cmd", "/c", trimmed)
    } else {
        trimmed.split(Regex("\\s+"))
    }
}

/**
 * jpackage app-image 的目录名。
 *
 * macOS 上 jpackage 按惯例给 app-image 加 `.app` 后缀（它就是应用身份本身），其余平台与 `--name` 同名。
 */
fun Platform.appImageDirName(appName: String) = if (this == Platform.DARWIN) "$appName.app" else appName
