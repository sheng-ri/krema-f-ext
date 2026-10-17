/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import top.birthcat.kremaf.ext.gradle.domain.BundleIcons
import top.birthcat.kremaf.ext.gradle.domain.BundleSettings
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.PlatformDetector
import top.birthcat.kremaf.ext.gradle.domain.resolveBundleIcon

/**
 * 单一裁决点：ext 覆盖 kremaConfig 的优先级在此算清一次，全程惰性、全 provider，task 在注册点直接 bind。
 *
 * 不依赖 `Project`：调用方把 `layout.projectDirectory` 传进来即可，故不引用 `Project` 类型。
 * 持有的 `ext`、`kremaConfig` 与 `projectDir` 皆配置缓存可序列化；只在注册期闭包里当局部值用，
 * 把算好的 provider 链 convention 给各 task，自身不出 task 字段。
 */
class ResolvedKremaConfig(
    private val ext: KremaExtension,
    private val kremaConfig: Provider<KremaConfig>,
    private val projectDir: Directory,
    buildDir: DirectoryProperty,
) {
    val outputDir = ext.outputDir.orElse(buildDir.dir("krema"))
    val bundleDir = outputDir.map { it.dir(BUNDLE_DIR_NAME) }
    val runtimeModules = ext.runtime.modules

    /** 前端构建命令：kremaConfig 为底，`krema.frontend.buildbCommand` 覆盖。 */
    val buildCommand =
        ext.frontend.buildCommand.orElse(kremaConfig.map { it.frontend.buildCommand.required(FRONTEND_COMMAND_HINT) })

    /**
     * 前端依赖安装命令：kremaConfig 为底，`krema.frontend.installCommand` 覆盖。
     * 不配或配了空白即 absent，`kremaFrontendBuild` 里 `orNull` 跳过安装。
     */
    val installCommand =
        ext.frontend.installCommand
            .orElse(kremaConfig.map { it.frontend.installCommand })

    /** 前端产物目录，取自 kremaConfig 的 `build.fronend_out_dir`。 */
    val frontendOutDir =
        ext.frontend.dir
            .orElse(kremaConfig.map { it.build.frontendOutDir })
            .map { projectDir.dir(it.required(FRONTEND_OUT_DIR_HINT)) }

    /** 前端工程目录：ext.dir 覆盖 kremaConfig，都没写即项目根。 */
    val frontendDir =
        ext.frontend.dir
            .orElse(kremaConfig.map { it.frontend.dir ?: "." })
            .map { projectDir.dir(it) }

    /**
     * 主类：ext 覆盖 kremaConfig，都没写即报错。
     */
    val mainClass =
        ext.mainClass
            .flatMap { v -> if (v.isNotBlank()) ext.mainClass else kremaConfig.map { it.build.mainClass } }
            .orElse(kremaConfig.map { it.build.mainClass })
            .map { it.required(MAIN_CLASS_HINT) }

    /** 开发期前端地址：ext 覆盖 kremaConfig，都没写即报错。 */
    val devUrl =
        ext.devUrl.orElse(kremaConfig.map { it.build.devUrl }).map { it.required(DEV_URL_HINT) }

    /** 应用名，jar 与 app-image 目录都从它来。 */
    val appName = kremaConfig.map { it.packageName.required(PACKAGE_NAME_HINT) }

    /** 打包输入，含 kremaConfig 内双来源 identifier（`bundle.identifier ?: package.identifier`）。 */
    val bundleSettings =
        kremaConfig.map { loaded ->
            val icon =
                when (PlatformDetector.detect()) {
                    Platform.DARWIN -> ext.bundling.macIcon.orNull
                    Platform.WINDOWS -> ext.bundling.winIcon.orNull
                    Platform.LINUX -> ext.bundling.linuxIcon.orNull
                    else -> null
                }?.asFile?.toPath() ?: loaded.bundle.icon?.let {
                    projectDir.asFile.toPath().resolve(it)
                }
            BundleSettings(
                appName = loaded.packageName.required(PACKAGE_NAME_HINT),
                identifier = loaded.bundle.identifier ?: loaded.identifier.required(IDENTIFIER_HINT),
                version = loaded.version.required(VERSION_HINT),
                mainClass = mainClass.get().required(MAIN_CLASS_HINT),
                icon = icon,
            )
        }

}

private const val FRONTEND_COMMAND_HINT =
    "frontend build command: krema.frontend.buildCommand or build.frontend_command in krema.toml"
private const val FRONTEND_OUT_DIR_HINT = "frontend output directory: build.frontend_out_dir in krema.toml"
private const val MAIN_CLASS_HINT = "main class: krema.mainClass or build.main_class in krema.toml"
private const val DEV_URL_HINT = "dev frontend URL: krema.devUrl or build.dev_url in krema.toml"
private const val IDENTIFIER_HINT = "package identifier: package.identifier in krema.toml"
private const val VERSION_HINT = "application version: package.version in krema.toml"
