/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package cnb.cool.birthcat.krema.spi.port;

import java.nio.file.Path;

/**
 * 喂给编排层的全部输入。
 *
 * <p>这是一个纯数据 record：只带路径与配置，不带任何构建工具类型。中间层用它，Gradle 与 Maven 各自 把它填好，编排逻辑对它一无所知。
 *
 * @param projectDir 项目根目录，也是 {@code krema.toml} 的解析基准
 * @param configFile 解析基准下的 {@code krema.toml}
 * @param outputDir 本次构建的产物根目录
 */
public record KremaProject(Path projectDir, Path configFile, Path outputDir) {}
