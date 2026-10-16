/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.tasks.JavaExec
import top.birthcat.kremaf.ext.gradle.KremaTaskNames

/**
 * 开发期：在 classpath 上起后端应用进程，把前端 dev server 地址作为环境变量 `KREMA_DEV_URL` 注入。
 *
 * 继承 Gradle 的 `JavaExec`：classpath 取 `runtimeClasspath`、主类取 `krema.mainClass` 或
 * `krema.toml` 的 `[build].main_class`，前台运行、构建被中断时由 Gradle 回收子进程。
 * 不依赖 `kremaBuild` / `kremaFrontendBuild`：前端由使用方自己的 dev server 提供。
 *
 * 覆盖值（主类、devUrl）由注册期从 [top.birthcat.kremaf.ext.gradle.config.ResolvedKremaConfig] 直接 bind：
 * `devUrl` 取值：`krema.toml` 的 `[build].dev_url` 作底，`krema { devUrl }` 覆盖，两处都没写即报错。
 * 注入只是给应用代码一个约定键 —— 用不用归用户代码。
 */
abstract class KremaDevRunTask : JavaExec() {
    init {
        group = KremaTaskNames.GROUP
        description = "Run the backend app on the classpath, injecting KREMA_DEV_URL; long-lived, not part of CI gating"
    }
}
