/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * 喂给编排层的全部输入。
 *
 * 用 Kotlin `data class` 而非 Java `record`：需要默认值，构造 `KremaProject(dir)` 即可，不必为嵌套结构写一堆构造器。
 * 性质与 `record` 时代一致 —— 纯数据，不含任何构建工具类型。
 *
 * @property dir 项目根目录，也是 `krema.toml` 的解析基准
 * @property config `krema.toml` 的解析结果
 * @property java Java 构建输入
 * @property frontend 前端构建输入
 * @property bundle 打包输入
 */
data class KremaProject(
    val dir: Path,
    val config: KremaConfig = KremaConfig(),
    val java: JavaProject = JavaProject(),
    val frontend: Frontend = Frontend(),
    val bundle: BundleSettings = BundleSettings(),
)

/** `krema.toml` 的解析结果，不保留 TOML 库类型。 */
data class KremaConfig(
    val packageName: String = "krema-app",
    val version: String = "0.1.0",
    val identifier: String = "com.example.app",
    val description: String = "",
    val window: Window = Window(),
    val build: Build = Build(),
    val frontend: Frontend = Frontend(),
    val bundle: Bundle = Bundle(),
    val permissions: List<String> = emptyList(),
    val updaterPubkey: String? = null,
    val deepLinkSchemes: List<String> = emptyList(),
)

/** 窗口默认值，透传给应用本身。 */
data class Window(
    val title: String = "Krema App",
    val width: Int = 1024,
    val height: Int = 768,
    val minWidth: Int = 0,
    val minHeight: Int = 0,
    val resizable: Boolean = true,
    val fullscreen: Boolean = false,
    val decorations: Boolean = true,
)

/**
 * 构建约定：源码与资源目录、主类。
 *
 * 前端命令不在这里 —— 它们是 [Frontend] 的字段。同一件事只留一个住所，
 * 否则 loader 填一间、编排层读另一间，两间之间没有任何代码相连。
 */
data class Build(
    val outDir: String = "dist",
    val javaSourceDir: String = "src-java",
    val mainClass: String? = null,
    val assetsPath: String = "assets",
)

/** 打包约定：图标、标识与双平台签名配置。 */
data class Bundle(
    val icon: String? = null,
    val identifier: String? = null,
    val copyright: String? = null,
    val macOS: MacOSSigning = MacOSSigning(),
    val windows: WindowsSigning = WindowsSigning(),
)

/** macOS 签名与公证配置。 */
data class MacOSSigning(
    val signingIdentity: String? = null,
    val entitlements: String? = null,
    val notarizationAppleId: String? = null,
    val notarizationTeamId: String? = null,
)

/** Windows 签名配置。 */
data class WindowsSigning(
    val signingCertificate: String? = null,
    val timestampUrl: String = "http://timestamp.digicert.com",
)

/** Java 构建输入：源码根目录、主类与编译开关。 */
data class JavaProject(
    val mainClass: String? = null,
    val sourceDir: Path? = null,
    val compile: Boolean = true,
)

/** 前端构建输入：命令拼装只认这里，不认锁文件。 */
data class Frontend(
    val enabled: Boolean = true,
    val buildCommand: String = "npm run build",
    val devCommand: String = "npm run dev",
    val devUrl: String = "http://localhost:5173",
    val workingDir: Path? = null,
)

/** 打包输入：产物名、图标与深链。 */
data class BundleSettings(
    val appName: String = "krema-app",
    val outputDir: Path = Path.of("."),
    val identifier: String = "com.example.app",
    val version: String = "0.1.0",
    val mainClass: String = "com.example.app.Main",
    val icon: Path? = null,
    val deepLinkSchemes: List<String> = emptyList(),
)
