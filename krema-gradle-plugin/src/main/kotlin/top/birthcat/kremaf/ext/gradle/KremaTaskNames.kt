/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

/** `krema` 分组下 task 的名字，TestKit 与 README 都从这里取。 */
object KremaTaskNames {
    const val GROUP = "krema"
    const val INIT = "kremaInit"
    const val BUILD = "kremaBuild"

    /** 临时可运行的 bundle：jpackage 的 app-image 目录（不含 zip），供 `kremaBundleRun` 直接起。 */
    const val BUNDLE = "kremaBundle"

    /** 平台包（platform）：本平台原生的可安装件，各格式在 #62 接上。 */
    const val BUNDLE_PLATFORM = "kremaBundlePlatform"

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
     * 这两张挂在 krema 分组却用 `jdeps` 前缀（其余 task 一律 `krema` 前缀）：它们对应的正是 jdeps
     * 的两个互斥开关——`-verbose` 与 `--print-module-deps` 不能同时给，同一张 task 切不出这个差别。
     */
    const val JDEPS_MODULES = "jdepsModules"

    const val JDEPS_MODULES_VERBOSE = "jdepsModulesVerbose"

    /** 全部 task 名，声明顺序即 `gradlew tasks` 里的出现顺序。 */
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
            BUNDLE_RUN,
            SIGNER_GENERATE,
            SIGNER_SIGN,
        )
}
