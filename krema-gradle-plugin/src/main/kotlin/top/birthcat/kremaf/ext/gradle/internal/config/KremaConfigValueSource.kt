/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.config

import org.gradle.api.Describable
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.nio.file.Files
import java.nio.file.Path

/** ValueSource 参数：配置路径与是否显式指定。 */
internal interface KremaConfigParameters : ValueSourceParameters {
    /** krema.toml 的绝对路径。 */
    val configFilePath: Property<String>

    /** 区分显式指定与默认路径：只有前者缺失才报错。 */
    val configFileSetExplicitly: Property<Boolean>
}

/**
 * krema.toml 的唯一解析入口。
 *
 * 走 ValueSource：值本身即跟踪键，Gradle 自动失效配置缓存。
 * 每次构建只 obtain 一次，多个 task 共用同一份结果。
 */
internal abstract class KremaConfigValueSource :
    ValueSource<KremaConfig, KremaConfigParameters>,
    Describable {
    /** 显式指定却缺失时报错，提示跑 kremaInit 补文件。 */
    @Throws(IllegalStateException::class)
    override fun obtain(): KremaConfig {
        val toml = Path.of(parameters.configFilePath.get())
        return when {
            Files.exists(toml) -> KremaConfigLoader().loadOrDefault(toml)
            parameters.configFileSetExplicitly.get() -> error("krema.configFile points to a missing krema.toml: $toml")
            else -> KremaConfig()
        }
    }

    /** 缓存失效信息里显示的名字。 */
    override fun getDisplayName() = KREMA_CONFIG_FILE_NAME
}
