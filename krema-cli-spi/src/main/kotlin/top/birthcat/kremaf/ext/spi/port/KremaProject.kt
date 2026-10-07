/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.nio.file.Path

/**
 * 喂给编排层的全部输入。
 *
 * 这是一个纯数据类：只带路径与配置，不带任何构建工具类型。中间层用它，Gradle 与 Maven 各自把它填好，编排逻辑对它一无所知。
 *
 * @property projectDir 项目根目录，也是 `krema.toml` 的解析基准
 * @property configFile 解析基准下的 `krema.toml`
 * @property outputDir 本次构建的产物根目录
 */
data class KremaProject(
    val projectDir: Path,
    val configFile: Path,
    val outputDir: Path,
)
