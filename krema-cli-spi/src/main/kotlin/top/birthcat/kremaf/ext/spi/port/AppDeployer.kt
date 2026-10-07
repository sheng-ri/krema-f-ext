/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * 产物落盘。
 *
 * 抽象层不认识 `target`，也不认识 `build`：bundle 根目录由调用方给，换构建工具只换那一个值。
 */
interface AppDeployer {
    /**
     * 把 classpath 与原生库放进已生成的 bundle。
     *
     * @param project 项目输入
     * @param bundleDir [BundleSupport.createProductionBundle] 的返回值
     * @param classpath 要打包的 runtime classpath
     * @return `bundleDir`，便于链式调用
     */
    fun deploy(
        project: KremaProject,
        bundleDir: Path,
        classpath: Classpath,
    ): Path
}
