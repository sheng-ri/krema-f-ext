/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.config

/** 前端资源在 assets jar 里的固定目录名。 */
internal const val ASSETS_DIR = "assets"

/** 产物根下的 app-image 目录名。 */
internal const val BUNDLE_DIR_NAME = "app"

/** 运行期依赖 jar 的目录名。 */
internal const val BUNDLE_DEPS = "deps"

/** 产物根目录的默认名。 */
internal const val DEFAULT_OUTPUT_DIR_NAME = "krema"

/** 运行时镜像的目录名。 */
internal const val RUNTIME_DIR_NAME = "runtime"

/** `jdeps -v` 的默认报告文件名。 */
internal const val JDEPS_VERBOSE_REPORT_NAME = "jdeps-verbose.txt"

/** 未指定应用名时的占位名，仅 `kremaInit` 使用。 */
internal const val DEFAULT_APP_NAME = "krema-app"

/** 默认配置文件名。 */
internal const val KREMA_CONFIG_FILE_NAME = "krema.toml"

/** Java 源码根的默认值，与 Gradle main source set 一致。 */
internal const val DEFAULT_JAVA_SOURCE_DIR = "src/main/java"

/** 开发期前端地址的环境变量名。 */
internal const val DEV_URL_ENV = "KREMA_DEV_URL"

/** 进程回收服务的注册名。 */
internal const val PROCESS_CLEANUP_SERVICE = "kremaProcessCleanup"

/** 平台包目录：`platform/<format id>`。 */
internal fun platformOutputDirName(formatId: String) = "platform/$formatId"

/** app jar 名，只由应用名决定。 */
internal fun appJarName(appName: String) = "$appName.jar"

/** assets jar 名，只由应用名决定。 */
internal fun assetsJarName(appName: String) = "$appName-assets.jar"

/** 格式名转 task 名：`APP_IMAGE` → `kremaPlatformAppImage`。 */
internal fun kremaPlatformTaskName(name: String) =
    "kremaPlatform" + name.split('_').joinToString("") { it.lowercase().replaceFirstChar(Char::uppercase) }
