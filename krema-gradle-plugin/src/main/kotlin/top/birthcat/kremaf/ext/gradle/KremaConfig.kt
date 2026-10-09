/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import java.nio.file.Path

/**
 * `krema.toml` 的解析结果，不保留 TOML 库类型。
 *
 * 只收在本仓库里真有消费者的键：窗口尺寸、权限列表、更新 pubkey、双平台签名配置这些在上游有语义、
 * 在这儿没人读的键不进骨架，避免「配了但不生效」的路再开一条。
 */
data class KremaConfig(
    val packageName: String = "krema-app",
    val version: String = "0.1.0",
    val identifier: String = "com.example.app",
    val build: Build = Build(),
    val frontend: Frontend = Frontend(),
    val bundle: Bundle = Bundle(),
    val deepLinkSchemes: List<String> = emptyList(),
)

/** 构建约定：产物目录、源码目录、主类。 */
data class Build(
    val outDir: String = "dist",
    val javaSourceDir: String = "src-java",
    val mainClass: String? = null,
)

/** 打包约定：图标与标识。 */
data class Bundle(
    val icon: String? = null,
    val identifier: String? = null,
    val copyright: String? = null,
)

/** 前端构建输入：命令拼装只认这里，不认锁文件。 */
data class Frontend(
    val buildCommand: String = "npm run build",
    val devCommand: String = "npm run dev",
    val devUrl: String = "http://localhost:5173",
    val workingDir: Path? = null,
)
