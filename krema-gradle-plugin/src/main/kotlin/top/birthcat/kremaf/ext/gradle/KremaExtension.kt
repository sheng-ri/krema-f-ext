/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Internal

/**
 * `krema { }` 扩展。
 *
 * 三件 Gradle 差异（classpath / 前端命令 / 输出目录）之外的构建输入全在 `krema.toml`。
 * 这里只让用户**覆盖**，不给默认值 —— 默认值的唯一住所是 `krema.toml` 与 `Frontend()`。
 *
 * 前端输入收在 [frontend] 里而不是平铺：命令属于前端域，铺到顶层就会长出
 * `frontendCommand` / `frontendDevCommand` 一串前缀，且与 `krema.toml` 的 `[build]`
 * 键名对齐的需要靠前缀硬撑。
 */
abstract class KremaExtension {
    /** 项目根目录下的 krema.toml。 */
    abstract val configFile: RegularFileProperty

    /** 产物根目录，默认 build/krema。 */
    abstract val outputDir: DirectoryProperty

    /** 主类，覆盖 `krema.toml` 的 `[build].main_class`。 */
    abstract val mainClass: Property<String>

    /** 前端输入，由插件在 `apply` 时接上 `frontend { }` 块。 */
    @Internal
    lateinit var frontend: KremaFrontendExtension

    /** 平台包输入，由插件在 `apply` 时接上 `bundling { }` 块。 */
    @Internal
    lateinit var bundling: KremaBundlingExtension
}

/**
 * `krema.frontend { }`。
 *
 * 字段名与 `krema.toml` 的 `frontend_*` 键一一对应，只是块名已经吞掉了 `frontend_` 前缀，
 * 所以这里叫 `buildCommand` 而不是 `frontendBuildCommand`。
 *
 * 全部字段是 Gradle `Property`：`buildCommand = "npm run build"` 即赋值（`Property` 由 Gradle 支持
 * Kotlin 赋值重载），也可以 `buildCommand.set(...)`。
 */
abstract class KremaFrontendExtension {
    /** 前端生产构建命令，覆盖 `krema.toml` 的 `[build].frontend_command`。 */
    abstract val buildCommand: Property<String>

    /** 前端 dev 命令，覆盖 `krema.toml` 的 `[build].frontend_dev_command`。 */
    abstract val devCommand: Property<String>

    /** 前端 dev server 地址，覆盖 `krema.toml` 的 `[build].frontend_dev_url`。 */
    abstract val devUrl: Property<String>
}

/**
 * `krema.bundling { }`。
 *
 * 只管平台包（便携包不给配）：用户写一份跨平台的 `platformFormats`，构建时按宿主平台取子集，
 * 所以同一份构建脚本在哪个平台跑都成立，不需要「这台机器跳过哪几个格式」的开关。
 */
abstract class KremaBundlingExtension {
    /** 要出的平台包格式；写全平台也可，构建时只取宿主平台的子集。默认空集合 —— 空即本平台不出平台包。 */
    abstract val platformFormats: SetProperty<BundleFormat>

    /** 宿主平台上实际要出的平台包格式；宿主平台之外的格式在这里就被丢掉，不报错也不跳过 task。 */
    fun formatsForHost(platform: Platform = PlatformDetector.detect()): List<BundleFormat> =
        platformFormats.get().filter { it.platform == platform }
}
