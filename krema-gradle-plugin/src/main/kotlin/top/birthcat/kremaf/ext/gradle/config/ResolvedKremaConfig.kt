/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import java.nio.file.Path

/**
 * 单一裁决点：ext 覆盖 toml 的优先级在此算清一次，全程惰性、全 provider，task 在注册点直接 bind。
 *
 * 不依赖 `Project`：调用方把 `layout.projectDirectory` 传进来即可，故不引用 `Project` 类型。
 * 持有的 `ext`、`kremaConfig` 与 `projectDir` 皆配置缓存可序列化；只在注册期闭包里当局部值用，
 * 把算好的 provider 链 convention 给各 task，自身不出 task 字段。
 */
class ResolvedKremaConfig(
    private val ext: KremaExtension,
    private val kremaConfig: Provider<KremaConfig>,
    private val projectDir: Directory,
) {
    private val toml get() = kremaConfig

    /** 前端构建命令：toml 为底，`krema.frontend.buildCommand` 覆盖。 */
    val buildCommand: Provider<String> =
        ext.frontend.buildCommand.orElse(toml.map { it.frontend.buildCommand.required(FRONTEND_COMMAND_HINT) })

    /**
     * 前端依赖安装命令：toml 为底，`krema.frontend.installCommand` 覆盖。
     * 不配或配了空白即 absent，`kremaFrontendBuild` 里 `orNull` 跳过安装。
     */
    val installCommand: Provider<String> =
        ext.frontend.installCommand
            .orElse(toml.map { it.frontend.installCommand })
            .filter { it.isNotBlank() }

    /** 前端产物目录，取自 toml 的 `build.out_dir`。 */
    val outDir: Provider<String> = toml.map { it.build.outDir.required(OUT_DIR_HINT) }

    /** 前端工程目录：ext.dir 覆盖 toml，都没写即项目根。 */
    val frontendDir: Provider<Directory> =
        ext.frontend.dir
            .orElse(toml.map { it.frontend.dir ?: "." })
            .map { projectDir.dir(it) }

    /**
     * 主类：ext 覆盖 toml，都没写即报错。
     * blank 的 ext 值回落 toml：`flatMap` 里把「set 但空白」转成 absent，才轮到 `orElse` 取 toml，
     * 否则 `Property.map { null }` 是「present 的 null」不会触发回落。
     * 回落分支直接返回 `ext.mainClass` 而非 `project.provider { v }`：后者会把 `Project` 关进成为
     * 真输入的 provider 链，配置缓存无法序列化。
     */
    val mainClass: Provider<String> =
        ext.mainClass
            .flatMap { v -> if (v.isNotBlank()) ext.mainClass else toml.map { it.build.mainClass } }
            .orElse(toml.map { it.build.mainClass })
            .map { it.required(MAIN_CLASS_HINT) }

    /** 开发期前端地址：ext 覆盖 toml，都没写即报错。 */
    val devUrl: Provider<String> =
        ext.devUrl.orElse(toml.map { it.build.devUrl }).map { it.required(DEV_URL_HINT) }

    /** 应用名，jar 与 app-image 目录都从它来。 */
    val appName: Provider<String> = toml.map { it.packageName.required(PACKAGE_NAME_HINT) }

    /** 打包输入，含 toml 内双来源 identifier（`bundle.identifier ?: package.identifier`）。 */
    fun bundleSettings(outputDir: Path): Provider<BundleSettings> =
        mainClass.zip(toml) { mc, loaded ->
            BundleSettings(
                appName = loaded.packageName.required(PACKAGE_NAME_HINT),
                outputDir = outputDir,
                identifier = loaded.bundle.identifier ?: loaded.identifier.required(IDENTIFIER_HINT),
                version = loaded.version.required(VERSION_HINT),
                mainClass = mc,
                icon = loaded.bundle.icon?.let { projectDir.asFile.toPath().resolve(it) },
                deepLinkSchemes = loaded.deepLinkSchemes,
            )
        }
}

/** 产物根目录约定：ext.outputDir 覆盖，没写即 build/krema。 */
fun kremaOutputDir(
    ext: KremaExtension,
    buildDirectory: DirectoryProperty,
): Provider<Directory> = ext.outputDir.orElse(buildDirectory.dir("krema"))

private const val FRONTEND_COMMAND_HINT = "前端构建命令：krema.frontend.buildCommand 或 krema.toml 的 build.frontend_command"
private const val OUT_DIR_HINT = "前端产物目录：krema.toml 的 build.out_dir"
private const val MAIN_CLASS_HINT = "主类：krema.mainClass 或 krema.toml 的 build.main_class"
private const val DEV_URL_HINT = "开发期前端地址：krema.devUrl 或 krema.toml 的 build.dev_url"
private const val IDENTIFIER_HINT = "包标识：krema.toml 的 package.identifier"
private const val VERSION_HINT = "应用版本：krema.toml 的 package.version"
