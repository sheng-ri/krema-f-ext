/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import java.io.File
import java.util.Locale

/**
 * 平台探测。
 *
 * 自研：上游的 `build.krema.core.platform.PlatformDetector` 不再复用（编排能力全自研，不依赖上游制品）。
 * 探测口径按 JVM 的 `os.name` / `os.arch`，覆盖 Krema 支持的三个平台族。
 */
object PlatformDetector {
    /** 探测当前平台。 */
    fun detect(): Platform = Platform.of(System.getProperty("os.name"))

    /** 探测当前架构标识，如 `aarch64`、`x86_64`。 */
    fun getArch(): String = normalizeArch(System.getProperty("os.arch"))

    /**
     * 当前平台的标识，形如 `linux-x86_64`。
     *
     * 与上游 `Platform.getTarget()` 同形：只作平台身份标识给下游用，不是可配置的目标平台。
     */
    fun currentTarget(): String = "${detect().name.lowercase(Locale.ROOT)}-${getArch()}"

    /** 归一化架构别名。 */
    fun normalizeArch(arch: String): String =
        when (arch.lowercase(Locale.ROOT)) {
            "aarch64" -> "aarch64"
            "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            "x86", "i386", "i686" -> "x86"
            else -> arch.lowercase(Locale.ROOT)
        }

    /** 把基础库名格式化成当前平台的动态库文件名。 */
    fun formatLibraryName(
        baseName: String,
        platform: Platform = detect(),
    ): String =
        when (platform) {
            Platform.DARWIN -> "lib$baseName.dylib"
            Platform.LINUX -> "lib$baseName.so"
            Platform.WINDOWS -> "$baseName.dll"
            Platform.UNKNOWN -> baseName
        }
}

/** Krema 支持的平台族。 */
enum class Platform {
    DARWIN,
    LINUX,
    WINDOWS,
    UNKNOWN,
    ;

    /** 库文件名前缀，`lib` 或空。 */
    val libraryPrefix: String
        get() = if (this == WINDOWS) "" else "lib"

    companion object {
        /** 由 `os.name` 判定平台族。 */
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
internal fun executableSuffix(): String = if (File.separatorChar == '\\') ".exe" else ""
