/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import org.gradle.api.Describable
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.nio.file.Files
import java.nio.file.Path

/**
 * `krema.toml` 的读取参数：路径 + 是否显式指定。
 *
 * 走 ValueSource 而不是 task 上的 `@Input` 路径属性：值本身即跟踪键，Gradle 自动跟踪 toml 变更并失效配置缓存，
 * 不必自己声明文件输入。
 */
interface KremaConfigParameters : ValueSourceParameters {
    /** `krema.toml` 的绝对路径。 */
    val configFilePath: Property<String>

    /**
     * 用户是否在 `krema { }` 里显式给了 `configFile`。
     *
     * 区分「显式指定」与「走默认路径」：只有显式指定的文件不存在才硬报错，默认路径缺失只是无值。
     */
    val configFileSetExplicitly: Property<Boolean>
}

/**
 * `krema.toml` 的唯一解析入口。
 *
 * 每次构建 [obtain] 只调一次，全构建共用这一份结果 —— `providers.provider {}` 每次取值都重跑闭包，
 * 一次构建 6-8 个 task 各解析一遍同一份 TOML。
 */
abstract class KremaConfigValueSource :
    ValueSource<KremaConfig, KremaConfigParameters>,
    Describable {
    /**
     * 显式指定却缺失时抛 [IllegalStateException]，报错指到 `kremaInit` 让用户补文件。
     */
    @Throws(IllegalStateException::class)
    override fun obtain(): KremaConfig {
        val toml = Path.of(parameters.configFilePath.get())
        return when {
            Files.exists(toml) -> KremaConfigLoader().loadOrDefault(toml)
            parameters.configFileSetExplicitly.get() -> error("krema.configFile 指向的 krema.toml 不存在：$toml")
            else -> KremaConfig()
        }
    }

    /** 缓存失效信息里显示这个名字，比类名可读。 */
    override fun getDisplayName(): String = "krema.toml"
}
