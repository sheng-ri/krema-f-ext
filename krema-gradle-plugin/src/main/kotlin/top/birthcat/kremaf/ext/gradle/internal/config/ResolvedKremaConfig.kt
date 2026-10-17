/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.config

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import top.birthcat.kremaf.ext.gradle.api.BundleSettings
import top.birthcat.kremaf.ext.gradle.api.KremaExtension
import top.birthcat.kremaf.ext.gradle.api.Platform
import top.birthcat.kremaf.ext.gradle.api.WindowsInstallerOptions
import top.birthcat.kremaf.ext.gradle.internal.domain.PlatformDetector

/**
 * ext 覆盖 krema.toml 的唯一裁决点。
 *
 * 注册期把 provider 链 convention 给 task，自身不进 task 字段。
 */
internal class ResolvedKremaConfig(
    private val ext: KremaExtension,
    private val kremaConfig: Provider<KremaConfig>,
    private val projectDir: Directory,
    buildDir: DirectoryProperty,
) {
    /** 产物根，固定 `build/krema`。 */
    val outputDir = buildDir.dir(DEFAULT_OUTPUT_DIR_NAME)

    /** app-image 落点，即 `outputDir/app`。 */
    val bundleDir = outputDir.map { it.dir(BUNDLE_DIR_NAME) }

    /** 显式模块清单；空集由 jdeps 推导。 */
    val runtimeModules = ext.runtime.modules

    /** 前端构建命令，ext 优先。 */
    val buildCommand =
        ext.frontend.buildCommand.orElse(kremaConfig.map { it.frontend.buildCommand.required(FRONTEND_COMMAND_HINT) })

    /** 前端安装命令；缺省即不装。 */
    val installCommand =
        ext.frontend.installCommand
            .orElse(kremaConfig.map { it.frontend.installCommand })

    /** 前端产物目录：ext 优先，缺配置即报错。 */
    val frontendOutDir =
        ext.frontendOutDir
            .map { it.asFile.absolutePath }
            .orElse(kremaConfig.map { it.build.frontendOutDir ?: it.build.outDir })
            .map { projectDir.dir(it.required(FRONTEND_OUT_DIR_HINT)) }

    /** Java 源码根；缺省落回 Gradle 默认。SrcDir 不接受无值 provider，故始终有值。 */
    val javaSourceDir =
        kremaConfig.map { config -> projectDir.dir(config.build.javaSourceDir ?: DEFAULT_JAVA_SOURCE_DIR) }

    /** 前端工程目录，缺省项目根。 */
    val frontendDir =
        ext.frontend.dir
            .orElse(kremaConfig.map { it.frontend.dir ?: "." })
            .map { projectDir.dir(it) }

    /** 主类，ext 优先，缺配置即报错。 */
    val mainClass =
        ext.mainClass
            .flatMap { v -> if (v.isNotBlank()) ext.mainClass else kremaConfig.map { it.build.mainClass } }
            .orElse(kremaConfig.map { it.build.mainClass })
            .map { it.required(MAIN_CLASS_HINT) }

    /** 开发期前端地址，缺配置即报错。 */
    val devUrl =
        ext.devUrl
            .orElse(kremaConfig.map { it.build.frontendDevUrl ?: it.build.devUrl })
            .map { it.required(DEV_URL_HINT) }

    /** 应用名，jar 与 app-image 目录都取自它。 */
    val appName = kremaConfig.map { it.packageName.required(PACKAGE_NAME_HINT) }

    /**
     * Windows 安装器参数；ext 逐项压过 toml，只挂到 Windows 格式的 task 上。
     *
     * 目录选择、桌面快捷方式、快捷方式询问三项缺省开；其它布尔项缺省不传，保留 jpackage 行为。
     */
    val windowsInstallerOptions =
        kremaConfig.map { loaded ->
            val windows = ext.bundling.windows
            WindowsInstallerOptions(
                dirChooser = windows.dirChooser.orNull ?: loaded.windows.dirChooser ?: true,
                helpUrl = windows.helpUrl.orNull ?: loaded.windows.helpUrl,
                menu = windows.menu.orNull ?: loaded.windows.menu,
                menuGroup = windows.menuGroup.orNull ?: loaded.windows.menuGroup,
                perUserInstall = windows.perUserInstall.orNull ?: loaded.windows.perUserInstall,
                shortcut = windows.shortcut.orNull ?: loaded.windows.shortcut ?: true,
                shortcutPrompt = windows.shortcutPrompt.orNull ?: loaded.windows.shortcutPrompt ?: true,
                updateUrl = windows.updateUrl.orNull ?: loaded.windows.updateUrl,
                upgradeUuid = windows.upgradeUuid.orNull ?: loaded.windows.upgradeUuid,
            )
        }

    /** 打包身份输入；图标先取 ext，再退到 toml。 */
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
                description = loaded.description,
                copyright = loaded.bundle.copyright,
            )
        }
}

private const val FRONTEND_COMMAND_HINT =
    "frontend build command: krema.frontend.buildCommand or build.frontend_command in krema.toml"
private const val FRONTEND_OUT_DIR_HINT =
    "frontend output directory: build.out_dir or build.frontend_out_dir in krema.toml"
private const val MAIN_CLASS_HINT = "main class: krema.mainClass or build.main_class in krema.toml"
private const val DEV_URL_HINT =
    "dev frontend URL: krema.devUrl or build.frontend_dev_url (build.dev_url also accepted) in krema.toml"
private const val IDENTIFIER_HINT = "package identifier: package.identifier in krema.toml"
private const val VERSION_HINT = "application version: package.version in krema.toml"
