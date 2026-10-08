/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * 平台 bundler 的取用口。
 *
 * bundler 能力自研：接口形状由本模块定，平台实现由
 * [top.birthcat.kremaf.ext.spi.iml.delegate.BundleSupportFactoryDelegate] 提供。
 */
fun interface BundleSupportFactory {
    /**
     * 取一个平台 bundler。
     *
     * @param target 目标平台标识，形如 `darwin-aarch64`、`windows-x86_64`、`linux-x86_64`
     * @return 该平台的 bundler
     */
    fun create(target: String): BundleSupport
}

/**
 * 平台打包能力。
 *
 * dev bundle 给「原生特性需要正经 bundle 标识」的平台用（macOS 是唯一一个）。
 */
interface BundleSupport {
    /** 目标平台标识，与 [BundleSupportFactory.create] 的入参一致。 */
    val target: String

    /** 该平台是否必须有 bundle 才能用原生特性。 */
    val requiresBundleForNativeFeatures: Boolean

    /**
     * 生成 bundle。
     *
     * dev / prod 两个编年收成一条：mac 手搓 `.app` 的 `KremaDev` 键随 [dev] 走，
     * 其余平台两种编年同形。
     *
     * @param settings 打包输入
     * @param dev true 出开发用 bundle，false 出可分发的生产 bundle
     * @return bundle 根目录，macOS 上是 `.app` 目录
     */
    fun createBundle(
        settings: BundleSettings,
        dev: Boolean,
    ): Path

    /**
     * 通过 bundle 启动应用。
     *
     * 环境变量不进签名：调用方塞给 `ProcessBuilder.environment()`，这里只认 [LaunchSettings]。
     *
     * @param settings 启动输入
     * @return 应用进程句柄
     */
    fun run(settings: LaunchSettings): Process
}

/**
 * 启动一个已打好的 bundle 所需的全部输入。
 *
 * 与 [BundleSettings] 并列住 port：打包与启动是两个动作，输入不混。
 *
 * @property bundlePath bundle 根目录，[BundleSupport.createBundle] 的返回值
 * @property classpath 应用启动时的 `-cp` 条目
 * @property mainClass 主类，覆盖 bundle 里写死的那个
 * @property devUrl 前端 dev server 地址，dev 启动时透传给应用
 * @property libPath 原生库目录，可能不存在
 * @property handshake true 时转 `Pipe` 判握手标志行再把流接回终端，false 时 `inheritIO()`
 */
data class LaunchSettings(
    val bundlePath: Path,
    val classpath: List<Path>,
    val mainClass: String,
    val devUrl: String? = null,
    val libPath: Path? = null,
    val handshake: Boolean = false,
)
