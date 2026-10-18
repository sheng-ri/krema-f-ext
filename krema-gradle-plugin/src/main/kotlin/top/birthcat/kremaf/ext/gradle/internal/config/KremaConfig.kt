/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.config

import java.io.Serializable

/**
 * krema.toml 的解析结果，不保留 TOML 类型。
 *
 * 必填项保持可空，缺哪项由取值处的 [required] 报错。
 * 实现 Serializable 是为了进配置缓存。
 */
internal data class KremaConfig(
    val packageName: String? = null,
    val version: String? = null,
    val identifier: String? = null,
    val description: String? = null,
    val build: Build = Build(),
    val frontend: Frontend = Frontend(),
    val bundle: Bundle = Bundle(),
    val windows: Windows = Windows(),
) : Serializable

/** [build] 段：主类、产物目录与 dev 地址。 */
internal data class Build(
    val frontendOutDir: String? = null,
    val outDir: String? = null,
    val javaSourceDir: String? = null,
    val mainClass: String? = null,
    val frontendDevUrl: String? = null,
    val devUrl: String? = null,
) : Serializable

/** [bundle] 段：图标与包标识。 */
internal data class Bundle(
    val icon: String? = null,
    val identifier: String? = null,
    val copyright: String? = null,
) : Serializable

/** [bundle.windows] 段：jpackage 的 --win-* 参数，仅 exe/msi 安装器认。 */
internal data class Windows(
    val dirChooser: Boolean? = null,
    val helpUrl: String? = null,
    val menu: Boolean? = null,
    val menuGroup: String? = null,
    val perUserInstall: Boolean? = null,
    val shortcut: Boolean? = null,
    val shortcutPrompt: Boolean? = null,
    val updateUrl: String? = null,
    val upgradeUuid: String? = null,
) : Serializable

/** [build] 里的 frontend_* 键，单列成前端输入。 */
internal data class Frontend(
    val dir: String? = null,
    val buildCommand: String? = null,
    val installCommand: String? = null,
) : Serializable

/** 必填项取值：缺失即报错，hint 指向具体配置项。 */
@Throws(IllegalStateException::class)
internal fun <T : Any> T?.required(hint: String) = this ?: error("Not configured: $hint")

/** 应用名的报错文案，多处共用。 */
internal const val PACKAGE_NAME_HINT = "application name: package.name in krema.toml"
