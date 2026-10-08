/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * 前端构建与 dev server。
 *
 * 只声明「要做什么」，进程怎么起由实现说了算（Gradle 侧走 `ExecOperations`）。
 * 环境变量不在口上：子进程继承宿主进程的环境，构建期要加变量就按各自构建工具的规矩来（Gradle 侧是 `export`），
 * 编排层不替它认 `.env`。
 */
interface FrontendBuilder {
    /**
     * 跑一次生产构建。
     *
     * @param project 项目输入，构建命令与工作目录取自 [KremaProject.frontend]
     * @return 前端产物目录，通常是 `<项目目录>/<outDir>`
     */
    fun build(project: KremaProject): Path

    /**
     * 启动 dev server。
     *
     * @param project 项目输入
     * @return 已启动的子进程句柄，调用方负责终止
     */
    fun startDevServer(project: KremaProject): Process

    /**
     * 探测 dev server 是否已在监听。
     *
     * @param url dev server 地址，取自 [KremaProject.frontend] 的 `devUrl`
     * @return 端口可连通时为 true
     */
    fun isDevServerUp(url: String): Boolean
}
