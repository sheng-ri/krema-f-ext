/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * `krema { }` 扩展：暴露全局默认值与 `[build]` 一道缝。
 *
 * 默认值唯一住所是 `KremaRegistry.Build()` 与 `krema.toml`，这里只让用户**覆盖**。
 */
abstract class KremaExtension {
    /** 项目根目录下的 krema.toml。 */
    abstract val configFile: RegularFileProperty

    /** 产物根目录，默认 build/krema。 */
    abstract val outputDir: DirectoryProperty

    /** 前端生产构建命令，覆盖 `krema.toml` 的 `[build].frontend_command`。 */
    abstract val frontendCommand: Property<String>

    /** 前端 dev 命令，覆盖 `krema.toml` 的 `[build].frontend_dev_command`。 */
    abstract val frontendDevCommand: Property<String>

    /** 前端 dev server 地址，覆盖 `krema.toml` 的 `[build].frontend_dev_url`。 */
    abstract val frontendDevUrl: Property<String>

    /** 主类，覆盖 `krema.toml` 的 `[build].main_class`。 */
    abstract val mainClass: Property<String>
}
