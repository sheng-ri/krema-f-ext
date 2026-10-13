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

    /** 便携包（dist）：jpackage 的 app-image 目录 + 一份 zip，解压即用。 */
    const val BUNDLE_DIST_DEV = "kremaBundleDistDev"
    const val BUNDLE_DIST_PROD = "kremaBundleDistProd"

    /** 平台包（platform）：本平台原生的可安装件，各格式在 #62 接上。 */
    const val BUNDLE_PLATFORM_DEV = "kremaBundlePlatformDev"
    const val BUNDLE_PLATFORM_PROD = "kremaBundlePlatformProd"

    const val BUNDLE_RUN_DEV = "kremaBundleRunDev"
    const val BUNDLE_RUN_PROD = "kremaBundleRunProd"
    const val SIGNER_GENERATE = "kremaSignerGenerate"
    const val SIGNER_SIGN = "kremaSignerSign"

    /** 用户面向的前端构建 task：跑前端生产构建、产可缓存的 `dist`；被 `kremaBuild` 依赖，也单独可跑。 */
    const val FRONTEND_BUILD = "kremaFrontendBuild"

    /** 运行时镜像 task：用 jdeps 推导模块、jlink 裁出最小运行时，产出 jpackage 要用的 `--runtime-image`。 */
    const val RUNTIME_IMAGE = "kremaRuntimeImage"

    /** 便携包 zip task 的名后缀：它是打包 task 的收尾，故不进 [ALL]。 */
    const val ZIP_SUFFIX = "Zip"

    /** 全部 task 名，声明顺序即 `gradlew tasks` 里的出现顺序。 */
    val ALL =
        listOf(
            INIT,
            BUILD,
            FRONTEND_BUILD,
            RUNTIME_IMAGE,
            BUNDLE_DIST_DEV,
            BUNDLE_DIST_PROD,
            BUNDLE_PLATFORM_DEV,
            BUNDLE_PLATFORM_PROD,
            BUNDLE_RUN_DEV,
            BUNDLE_RUN_PROD,
            SIGNER_GENERATE,
            SIGNER_SIGN,
        )
}
