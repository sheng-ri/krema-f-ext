/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import top.birthcat.kremaf.ext.gradle.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.config.KremaConfig
import top.birthcat.kremaf.ext.gradle.config.KremaConfigLoader
import java.nio.file.Path

/**
 * 开发期：在 classpath 上起后端应用进程，把前端 dev server 地址作为环境变量 `KREMA_DEV_URL` 注入。
 *
 * 继承 Gradle 的 `JavaExec`：classpath 取 `runtimeClasspath`、主类取 `krema.mainClass` 或
 * `krema.toml` 的 `[build].main_class`，前台运行、构建被中断时由 Gradle 回收子进程。
 * 不依赖 `kremaBuild` / `kremaFrontendBuild`：前端由使用方自己的 dev server 提供。
 *
 * `devUrl` 取值：`krema.toml` 的 `[build].dev_url` 作底，`krema { devUrl }` 覆盖，内建默认
 * `http://localhost:5173`。注入只是给应用代码一个约定键 —— 用不用归用户代码。
 */
abstract class KremaDevRunTask : org.gradle.api.tasks.JavaExec() {
    /** `krema.toml` 的绝对路径。 */
    @get:Internal
    abstract val configFilePath: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val configFilePresent: org.gradle.api.provider.Property<Boolean>

    @get:Input
    abstract val configFileSetExplicitly: org.gradle.api.provider.Property<Boolean>

    /** 主类，覆盖 `krema.toml` 的 `[build].main_class`。 */
    @get:Input
    @get:Optional
    abstract val mainClassOverride: org.gradle.api.provider.Property<String>

    /** 开发期前端地址，覆盖 `krema.toml` 的 `[build].dev_url`。 */
    @get:Input
    @get:Optional
    abstract val devUrlOverride: org.gradle.api.provider.Property<String>

    init {
        group = KremaTaskNames.GROUP
        description = "Run the backend app on the classpath, injecting KREMA_DEV_URL; long-lived, not part of CI gating"
    }

    private fun kremaConfig(): KremaConfig {
        val toml = Path.of(configFilePath.get())
        return if (configFilePresent.get()) {
            KremaConfigLoader().loadOrDefault(toml)
        } else if (configFileSetExplicitly.get()) {
            error("krema.configFile 指向的 krema.toml 不存在：$toml")
        } else {
            KremaConfig()
        }
    }

    /** 解析主类：`krema { }` 覆盖 `krema.toml` 的 `[build].main_class`，都没有时回落到 `<identifier>.Main`。 */
    internal fun resolvedMainClass(): String {
        val config = kremaConfig()
        return mainClassOverride.orNull ?: config.build.mainClass ?: "${config.identifier}.Main"
    }

    /** 解析 `KREMA_DEV_URL`：`krema { devUrl }` 覆盖 `[build].dev_url`，都没有时回落到内建默认。 */
    internal fun resolvedDevUrl(): String {
        val config = kremaConfig()
        return devUrlOverride.orNull ?: config.build.devUrl ?: DEFAULT_DEV_URL
    }

    private companion object {
        const val DEFAULT_DEV_URL = "http://localhost:5173"
    }
}
