/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

/** `krema` 分组下 task 的名字，注册处与 README 都从这里取。 */
object KremaTaskNames {
    const val GROUP = "krema"
    const val INIT = "kremaInit"
    const val BUILD = "kremaBuild"

    /** 未压缩、可直接运行的 app-image 目录：`kremaBundle` 产出，是所有平台包的共同底座。 */
    const val BUNDLE = "kremaBundle"

    /** 平台包聚合 task：依赖本平台配置了的各 `kremaPlatform<格式>` task，没配格式时空跑成功。 */
    const val BUNDLE_PLATFORM = "kremaBundlePlatform"

    /** 开发期：起 classpath 上的应用进程并注入 `KREMA_DEV_URL`。 */
    const val DEV_RUN = "kremaDevRun"

    const val BUNDLE_RUN = "kremaBundleRun"
    const val SIGNER_GENERATE = "kremaSignerGenerate"
    const val SIGNER_SIGN = "kremaSignerSign"

    /** 用户面向的前端构建 task：跑前端生产构建、产可缓存的 `dist`；被 `kremaBuild` 依赖，也单独可跑。 */
    const val FRONTEND_BUILD = "kremaFrontendBuild"

    /** 运行时镜像 task：用 jdeps 推导模块、jlink 裁出最小运行时，产出 jpackage 要用的 `--runtime-image`。 */
    const val RUNTIME_IMAGE = "kremaRuntimeImage"

    /**
     * jdeps 检视 task：打印推导出的模块清单 / `-v` 逐包明细。
     *
     * 这两种挂在 krema 分组却用 `jdeps` 前缀（其余 task 一律 `krema` 前缀）：它们对应的正是 jdeps
     * 的两个互斥开关——`-verbose` 与 `--print-module-deps` 不能同时给，同一种 task 切不出这个差别。
     */
    const val JDEPS_MODULES = "jdepsModules"

    const val JDEPS_MODULES_VERBOSE = "jdepsModulesVerbose"

    /** 全部静态 task 名，声明顺序即 `gradlew tasks` 里的出现顺序。按需注册的 `kremaPlatform<格式>` 不在此列。 */
    val ALL =
        listOf(
            INIT,
            BUILD,
            FRONTEND_BUILD,
            RUNTIME_IMAGE,
            JDEPS_MODULES,
            JDEPS_MODULES_VERBOSE,
            BUNDLE,
            BUNDLE_PLATFORM,
            DEV_RUN,
            BUNDLE_RUN,
            SIGNER_GENERATE,
            SIGNER_SIGN,
        )
}
