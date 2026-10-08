/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * Java 源码编译。
 *
 * 出参只有输出目录，不暴露编译器命令行 —— 编排层拼不出 `javac`，也就不可能悄悄退回构建工具的目录约定。
 * `native-image` 编译等 `kremaBuildNative` 落地时再往这里加方法。
 */
interface JavaCompiler {
    /**
     * 编译 `src` 下的源码。
     *
     * @param project 项目输入，源码目录取自 [KremaProject.java]
     * @param classpath 依赖 classpath，不含本项目输出目录
     * @return 编译输出目录，前端资源会被拷进这个目录的 assets 子目录
     */
    fun compile(
        project: KremaProject,
        classpath: Classpath,
    ): Path
}
