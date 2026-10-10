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
    const val BUNDLE_DEV = "kremaBundleDev"
    const val BUNDLE_PROD = "kremaBundleProd"
    const val BUNDLE_RUN_DEV = "kremaBundleRunDev"
    const val BUNDLE_RUN_PROD = "kremaBundleRunProd"
    const val SIGNER_GENERATE = "kremaSignerGenerate"
    const val SIGNER_SIGN = "kremaSignerSign"

    /** 用户面向的前端构建 task：跑前端生产构建、产可缓存的 `dist`；被 `kremaBuild` 依赖，也单独可跑。 */
    const val FRONTEND_BUILD = "kremaFrontendBuild"

    /** 运行时镜像 task：用 jdeps 推导模块、jlink 裁出最小运行时，产出 jpackage 要用的 `--runtime-image`。 */
    const val RUNTIME_IMAGE = "kremaRuntimeImage"

    /** 全部 task 名，声明顺序即 `gradlew tasks` 里的出现顺序。 */
    val ALL =
        listOf(
            INIT,
            BUILD,
            FRONTEND_BUILD,
            RUNTIME_IMAGE,
            BUNDLE_DEV,
            BUNDLE_PROD,
            BUNDLE_RUN_DEV,
            BUNDLE_RUN_PROD,
            SIGNER_GENERATE,
            SIGNER_SIGN,
        )
}
