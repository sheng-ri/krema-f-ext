/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

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

/**
 * bundle 的分发形态：分发形态 × 构建档位 = 四张打包 task，见 [top.birthcat.kremaf.ext.gradle.BundleForm]。
 */
enum class BundleForm(
    /** 进产物路径的稳定标识，与枚举名解耦。 */
    val id: String,
    /** 该形态的产物是否还要再打一份便携包 zip。 */
    val zipped: Boolean,
) {
    /** 便携包：不分平台的 app-image 目录 + zip，解压即用。 */
    DIST("dist", true),

    /** 平台包：本平台原生的可安装件，具体格式由 [BundleFormat] 选。 */
    PLATFORM("platform", false),
}

/** 当前平台的可执行文件后缀，Windows 上为 `.exe`。 */
internal fun executableSuffix(): String = if (File.separatorChar == '\\') ".exe" else ""

/**
 * jpackage app-image 的目录名。
 *
 * macOS 上 jpackage 按惯例给 app-image 加 `.app` 后缀（它就是应用身份本身），其余平台与 `--name` 同名。
 */
fun Platform.appImageDirName(appName: String): String = if (this == Platform.DARWIN) "$appName.app" else appName
