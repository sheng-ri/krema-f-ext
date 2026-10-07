/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty

/** `krema { }` 扩展：暴露全局默认值。 */
abstract class KremaExtension {
    /** 项目根目录下的 krema.toml。 */
    abstract val configFile: RegularFileProperty

    /** 产物根目录，默认 build/krema。 */
    abstract val outputDir: DirectoryProperty
}
