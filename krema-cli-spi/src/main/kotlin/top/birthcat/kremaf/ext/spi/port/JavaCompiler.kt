/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * Java 源码编译与 native-image 打包。
 *
 * 出参只有文件系统路径，不暴露编译器命令行 —— 编排层拼不出 `javac` / `native-image`，
 * 也就不可能悄悄退回构建工具的目录约定。classpath 一律由 [ClasspathResolver] 给，
 * 这里不认 Maven 也不认 Gradle。
 */
interface JavaCompiler {
    /**
     * 编译 `src-java` 下的源码。
     *
     * @param project 项目输入，源码目录取自 [KremaProject.java]
     * @param classpath 依赖 classpath，不含本项目输出目录
     * @return 编译输出目录，前端资源会被拷进这个目录的 assets 子目录
     */
    fun compile(
        project: KremaProject,
        classpath: Classpath,
    ): Path

    /**
     * 先 [compile]，再把编译结果打成 native 可执行文件。
     *
     * classpath 走 [Classpath]，不认得任何构建工具的目录约定（上游那句 `mvn dependency:build-classpath`
     * 是分级耦合的老账，本仓不复刻）。工具链按 `GRAALVM_HOME` → 解析出的 JDK → `PATH` 的顺序找。
     *
     * @param project 项目输入，主类取自 [KremaProject.java]，产物名取自 [KremaProject.config]
     * @param classpath 依赖 classpath 与原生库目录
     * @return 生成的 native 可执行文件路径
     */
    fun compileNative(
        project: KremaProject,
        classpath: Classpath,
    ): Path
}
