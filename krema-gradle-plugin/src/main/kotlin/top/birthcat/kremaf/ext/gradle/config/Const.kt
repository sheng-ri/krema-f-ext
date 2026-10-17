/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

/** 前端资源在 assets jar 里的固定目录名 */
const val ASSETS_DIR = "assets"

/** krema 产物根下的固定目录名，CI 上传规则照此写死。 */
const val BUNDLE_DIR_NAME = "app"

const val BUNDLE_DEPS = "deps"

/**
 * 与 [top.birthcat.kremaf.ext.gradle.domain.BundleFormat.id] 解耦拼写：平台包目录只拼 `platform/<id>` 层级，改 id 即挪路径。
 */
internal fun platformOutputDirName(formatId: String) = "bundle/$formatId"

/** 凭应用名拼 app jar 名（jar 名与主类无关，仅需 appName）。 */
internal fun appJarName(appName: String) = "$appName.jar"

/** 凭应用名拼 assets jar 名。 */
internal fun assetsJarName(appName: String) = "$appName-assets.jar"

internal fun kremaPlatformTaskName(name: String) =
    "kremaPlatform" + name.split('_').joinToString("") { it.lowercase().replaceFirstChar(Char::uppercase) }

internal const val PROCESS_CLEANUP_SERVICE = "kremaProcessCleanup"
