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
     * 生产 bundle 是否还要调用方自己把 classpath 摊进去。
     *
     * macOS 手搓 `.app`，布局由本模块定，所以要 `true`；Windows / Linux 交 jpackage，
     * runtime 镜像它自己装，调用方再摊一遍只会往安装包旁边倒垃圾。
     */
    val deploysClasspath: Boolean

    /**
     * 生成开发用 bundle，只够原生特性认出应用身份。
     *
     * @param settings 打包输入
     * @return 启动器所在路径
     */
    fun createDevBundle(settings: BundleSettings): Path

    /**
     * 生成可分发的生产 bundle。
     *
     * @param settings 打包输入
     * @return bundle 根目录，macOS 上是 `.app` 目录
     */
    fun createProductionBundle(settings: BundleSettings): Path

    /**
     * 通过 bundle 启动应用。
     *
     * @param bundlePath bundle 根目录
     * @param env 传给应用的额外环境变量
     * @return 应用进程句柄
     */
    fun launch(
        bundlePath: Path,
        env: Map<String, String>,
    ): Process
}
