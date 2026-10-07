/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.io.File
import java.nio.file.Path

/**
 * classpath 的来源。
 *
 * 这是中间层**唯一**的构建工具耦合点：Gradle 侧从 `Configuration` 取，Maven 侧从依赖解析取，
 * 将来接别的构建工具再实现一遍。编排层只拿到一个 [Classpath]，不知道它是怎么来的。
 */
fun interface ClasspathResolver {
    /**
     * 解析运行时与编译期 classpath。
     *
     * @param project 项目输入，提供目录与配置基准
     * @return classpath 条目与原生库目录
     */
    fun resolve(project: KremaProject): Classpath
}

/**
 * 一次解析的结果。
 *
 * @property entries classpath 条目，顺序即 JVM 的 `-cp` 顺序
 * @property classOutputDir 本项目编译输出目录，已含资源拷贝结果
 * @property nativeLibDir 原生 webview 库所在目录，可能不存在
 */
data class Classpath(
    val entries: List<Path>,
    val classOutputDir: Path,
    val nativeLibDir: Path? = null,
) {
    /** 按当前平台的路径分隔符拼成 `-cp` 参数。 */
    fun joined(): String = entries.joinToString(File.pathSeparator)
}
