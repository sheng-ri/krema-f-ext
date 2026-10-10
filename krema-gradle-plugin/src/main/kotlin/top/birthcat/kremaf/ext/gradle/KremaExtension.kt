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

    /**
     * 前端依赖安装命令，如 `pnpm install`。
     *
     * 用户自己写、用什么包管理器只有他知道，插件不猜：装了 pnpm 的项目按 pnpm 装，装了 yarn 的按 yarn 装，
     * 走哪个都行。不写就不装 —— 此时 `node_modules` 得由用户自己准备好。
     */
    abstract val installCommand: Property<String>

    /** 前端 dev 命令，覆盖 `krema.toml` 的 `[build].frontend_dev_command`。 */
    abstract val devCommand: Property<String>

    /** 前端 dev server 地址，覆盖 `krema.toml` 的 `[build].frontend_dev_url`。 */
    abstract val devUrl: Property<String>
}

/**
 * `krema.bundling { }`。
 *
 * 只管平台包（便携包不给配）。三个平台各一个属性，各自的构建档位与宿主平台对不上就整份忽略 ——
 * 于是换机器不用改构建脚本，也不需要「这台机器跳过哪几个格式」的开关。
 *
 * 分成三个属性而不是一份混合列表，是为了让读脚本的人一眼看出哪个格式归哪个平台：
 * 混在一个 `setOf` 里时，`DMG` 和 `MSI` 挨在一起，分不出谁是谁。
 */
abstract class KremaBundlingExtension {
    /** macOS 要出的平台包格式（`.app` / `.dmg` / `.pkg`）。默认空集合 —— 空即该平台不出平台包。 */
    abstract val macosFormats: SetProperty<BundleFormat>

    /** Windows 要出的平台包格式（`.exe` / `.msi`）。默认空集合。 */
    abstract val windowsFormats: SetProperty<BundleFormat>

    /** Linux 要出的平台包格式（`.AppImage` / `.deb` / `.rpm`）。默认空集合。 */
    abstract val linuxFormats: SetProperty<BundleFormat>

    /**
     * 宿主平台上实际要出的平台包格式；其余两个平台的格式在这里就被丢掉，不报错也不跳过 task。
     *
     * 按属性取，不按枚举自身的 [BundleFormat.platform] 过滤：属性名已经声明了归属，
     * 再过一遍会让「把 `DMG` 写进 `windowsFormats`」这种笔误被静默吞掉而不是被发现。
     */
    fun formatsForHost(platform: Platform = PlatformDetector.detect()): Set<BundleFormat> =
        when (platform) {
            Platform.DARWIN -> macosFormats.get()
            Platform.WINDOWS -> windowsFormats.get()
            Platform.LINUX -> linuxFormats.get()
            Platform.UNKNOWN -> emptySet()
        }
}
