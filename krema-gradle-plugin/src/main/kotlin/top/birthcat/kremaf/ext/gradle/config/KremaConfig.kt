/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import java.io.Serializable

/**
 * `krema.toml` 的解析结果，不保留 TOML 库类型。
 *
 * 只收在本仓库里真有消费者的键：窗口尺寸、权限列表、更新 pubkey、双平台签名配置这些在上游有语义、
 * 在这儿没人读的键不进骨架，避免「配了但不生效」的路再开一条。
 *
 * 必填项声明为可空：解析层只如实反映文件内容，缺哪项由取值处的 [required] 报错指到具体配置项。
 * 实现 [Serializable] 是因为它经 [KremaConfigValueSource] 进配置缓存。
 */
data class KremaConfig(
    val packageName: String? = null,
    val version: String? = null,
    val identifier: String? = null,
    val build: Build = Build(),
    val frontend: Frontend = Frontend(),
    val bundle: Bundle = Bundle(),
    val deepLinkSchemes: List<String> = emptyList(),
) : Serializable

/** 构建约定：产物目录与主类。前端产物的 classpath 落点由 Krema 约定为 `assets`，不开放配置。 */
data class Build(
    val outDir: String? = null,
    val mainClass: String? = null,
    val devUrl: String? = null,
) : Serializable

/** 打包层元数据约定：图标、标识、版权，只在本仓库的打包步骤被读取，故单列不进构建骨架。 */
data class Bundle(
    val icon: String? = null,
    val identifier: String? = null,
    val copyright: String? = null,
) : Serializable

/** 前端构建输入：前端目录与命令拼装只认这里，不认锁文件。 */
data class Frontend(
    val dir: String? = null,
    val buildCommand: String? = null,
    val installCommand: String? = null,
) : Serializable

/**
 * 必填配置的取值口：缺失即报错，[hint] 指到具体配置项，让报错本身就能照着改。
 *
 * 不给兜底默认值：`0.1.0`、`dist` 这类占位值会让漏配的项目照样「构建成功」，
 * 直到产物带着别人的名字发出去才被人发现。
 */
@Throws(IllegalStateException::class)
internal fun <T : Any> T?.required(hint: String): T = this ?: error("未配置$hint")

/** 应用名的报错文案：产名、jar 名与 app-image 目录都从它来，故多处共用一句。 */
internal const val PACKAGE_NAME_HINT = "应用名：krema.toml 的 package.name"
