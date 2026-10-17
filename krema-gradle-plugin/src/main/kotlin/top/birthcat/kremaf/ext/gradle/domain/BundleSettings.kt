/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.domain

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import top.birthcat.kremaf.ext.gradle.config.appJarName
import java.io.Serializable
import java.nio.file.Path

/**
 * 打包输入：产物名、图标、深链，以及前端产物在 app jar classpath 上的落点。
 *
 * bundle 目录结构与启动器都由 jpackage 生成，这里只留「产物叫什么」这一层命名输入。
 */
data class BundleSettings(
    @get:Input val appName: String = "krema-app",
    @get:Input val identifier: String = "com.example.app",
    @get:Input val version: String = "0.1.0",
    @get:Input val mainClass: String = "com.example.app.Main",
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    val icon: Path? = null,
) : Serializable {
    /** 对应 jpackage 的 `--main-jar`。 */
    @get:Internal
    val appJarName get() = appJarName(appName)
}

/**
 * 四个图标来源：按平台扩展图标优先，[toml] 兜底。四个恒定同行，打包时整组传入，不再散成五个参数。
 *
 * 全部可空、默认 null：没配的维度就是「无」，缺了不该阻断打包。
 */
data class BundleIcons(
    val mac: Path? = null,
    val windows: Path? = null,
    val linux: Path? = null,
)

/** 解析 jpackage `--icon` 路径：扩展图标优先，退到 [BundleIcons.toml]；都没配或不存在则返回 null 且不传 `--icon`（图标缺失不该阻断打包）。 */
fun BundleIcons.resolveBundleIcon(platform: Platform) =
    when (platform) {
        Platform.DARWIN -> mac
        Platform.WINDOWS -> windows
        Platform.LINUX -> linux
        Platform.UNKNOWN -> null
    }
