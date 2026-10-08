/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

/**
 * 目标交付物格式。
 *
 * 占位定义：完整的三平台子集分类（原始 / 可跑 / 可分发）、默认值与命令拼装归 #36，
 * 本单只借它把 [KremaBundleTask][top.birthcat.kremaf.ext.gradle.task.KremaBundleTask]
 * 的 `format` 形参接成一个**单点可校验**的值。**不要在本文件外扩展这张表** ——
 * #36 落地时会整体接管。
 */
enum class BundleFormat {
    /** Windows 的 exe 安装器。 */
    EXE,

    /** Linux 的 deb 包。 */
    DEB,

    /** macOS 手搓的 `.app` 目录树。 */
    APP,

    ;

    /** 解析 Gradle 传入的 format 字面量，认大小写与 `-` / `_` 两种分隔。 */
    companion object {
        fun parse(raw: String): BundleFormat =
            entries.firstOrNull { it.name.equals(normalize(raw), ignoreCase = true) }
                ?: error(
                    "未知的 bundle format '$raw' —— 可选值：${entries.joinToString("、") { it.name.lowercase() }}；" +
                        "或改用 `krema { bundle { ... } }` 声明（见 #36）",
                )

        private fun normalize(raw: String): String = raw.trim().replace('-', '_')
    }
}
