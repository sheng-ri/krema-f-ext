/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Internal
import top.birthcat.kremaf.ext.gradle.domain.BundleFormat
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.PlatformDetector

/**
 * `krema { }` 扩展。
 *
 * 三件 Gradle 差异（classpath / 前端命令 / 输出目录）之外的构建输入全在 `krema.toml`。
 * 这里只让用户**覆盖**，不给默认值 —— 默认值的唯一住所是 `krema.toml` 与 `Frontend()`。
 *
 * 前端输入收在 [frontend] 里而不是平铺：命令属于前端域，铺到顶层就会长出
 * `frontendCommand` 一串前缀，且与 `krema.toml` 的 `[build]` 键名对齐的需要靠前缀硬撑。
 */
abstract class KremaExtension {
    /** 项目根目录下的 krema.toml。 */
    abstract val configFile: RegularFileProperty

    /** 产物根目录，默认 build/krema。 */
    abstract val outputDir: DirectoryProperty

    /** 主类，覆盖 `krema.toml` 的 `[build].main_class`。 */
    abstract val mainClass: Property<String>

    /**
     * 开发期前端 dev server 地址，注入给 `kremaDevRun` 起的应用进程（环境变量 `KREMA_DEV_URL`）。
     *
     * 覆盖 `krema.toml` 的 `[build].dev_url`；两处都没写则报错 —— 没有默认值可回落，
     * 插件猜的端口未必是用户前端工具链实际监听的端口。
     * 只在这一处给值，应用代码要不要读那个环境变量归它自己。
     */
    abstract val devUrl: Property<String>

    abstract val frontendOutDir: DirectoryProperty

    /** 前端输入，由插件在 `apply` 时接上 `frontend { }` 块。 */
    @Internal
    lateinit var frontend: KremaFrontendExtension

    /** 平台包输入，由插件在 `apply` 时接上 `bundling { }` 块。 */
    @Internal
    lateinit var bundling: KremaBundlingExtension

    /** 运行时镜像输入，由插件在 `apply` 时接上 `runtime { }` 块。 */
    @Internal
    lateinit var runtime: KremaRuntimeExtension
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
    /**
     * 前端工程所在目录，相对项目根，覆盖 `krema.toml` 的 `[build].frontend_dir`；不写就是项目根。
     *
     * 取 `String` 而不是 `DirectoryProperty`：这一层与 toml 键接受的是同一个字面量，
     * 「扩展压过 toml」才只是替换而不是翻译；而把它变成目录要 `layout`，那是注册期才有的东西。
     */
    abstract val dir: Property<String>

    /** 前端生产构建命令，覆盖 `krema.toml` 的 `[build].frontend_command`。 */
    abstract val buildCommand: Property<String>

    /**
     * 前端依赖安装命令，如 `pnpm install`。
     *
     * 用户自己写、用什么包管理器只有他知道，插件不猜：装了 pnpm 的项目按 pnpm 装，装了 yarn 的按 yarn 装，
     * 走哪个都行。不写就不装 —— 此时 `node_modules` 得由用户自己准备好。
     */
    abstract val installCommand: Property<String>
}

/**
 * `krema.runtime { }`。
 *
 * 运行时镜像要打进哪些 JDK 模块。不写就让 jdeps 按应用 classpath 推导 —— 推导不出来时（反射、
 * 运行时才加载的类）才需要在这里显式补上。
 */
abstract class KremaRuntimeExtension {
    /**
     * 打进运行时镜像的 JDK 模块名，如 `java.base`。非空时**取代** jdeps 推导。
     *
     * 取代而不是追加：显式列清单的人是在对自己的 classpath 负责，把推导结果再叠上去等于把他删掉的
     * 模块偷偷加回来，也让「这份清单最终裁出什么」无法一眼看清。
     */
    abstract val modules: SetProperty<String>
}

/**
 * `krema.bundling { }`。
 *
 * 只管平台包（未压缩的 app-image 由 `kremaBundle` 产出，这里不配）。三个平台各一个属性，各自的格式与宿主平台对不上就整份忽略 ——
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
     * 各平台的图标：`macIcon` 吃 icns、`winIcon` 吃 ico、`linuxIcon` 吃 png。
     *
     * 未配或文件不存在就跳过 `--icon`，不报错。三种都未配时退到 `krema.toml` 的 `[bundle].icon`
     * 按宿主平台后缀兜底（见 [top.birthcat.kremaf.ext.gradle.domain.resolveBundleIcon]）。
     */
    abstract val macIcon: RegularFileProperty

    /** Windows 图标（ico）。 */
    abstract val winIcon: RegularFileProperty

    /** Linux 图标（png）。 */
    abstract val linuxIcon: RegularFileProperty

    /**
     * PATH 上没有 `appimagetool` 时是否自动下载一份到构建目录。默认 `true`。
     *
     * 仅影响 AppImage 这一条路径：其它格式走 jpackage，不碰 appimagetool。自动下载失败会明确报错，
     * 给出「手动装 / 关开关」两条出路，不会静默当成成功。
     */
    abstract val appImageToolAutoDownload: Property<Boolean>

    /**
     * 宿主平台上实际要出的平台包格式；其余两个平台的格式在这里就被丢掉，不报错也不跳过 task。
     *
     * 按属性取，不按枚举自身的 [BundleFormat.platform] 过滤：属性名已经声明了归属，
     * 再过一遍会让「把 `DMG` 写进 `windowsFormats`」这种笔误被静默吞掉而不是被发现。
     */
    fun formatsForHost() = selectHostFormats(PlatformDetector.detect(), macosFormats.get(), windowsFormats.get(), linuxFormats.get())
}

/**
 * 按宿主平台从三种属性里选要出的格式：把「跨平台写的格式列表只留宿主子集」这一层与 Gradle 的 `SetProperty` 解耦。
 */
fun selectHostFormats(
    platform: Platform,
    macos: Set<BundleFormat>,
    windows: Set<BundleFormat>,
    linux: Set<BundleFormat>,
) = when (platform) {
    Platform.DARWIN -> macos
    Platform.WINDOWS -> windows
    Platform.LINUX -> linux
    Platform.UNKNOWN -> emptySet<BundleFormat>()
}
