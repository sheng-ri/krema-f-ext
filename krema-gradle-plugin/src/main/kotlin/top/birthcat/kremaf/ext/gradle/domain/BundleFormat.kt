/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.domain

/**
 * 平台包格式，即本平台原生的可安装件。
 *
 * 用户在一份全局列表里写出所有想要的格式，构建时插件按 [Platform] 取出宿主平台的子集 ——
 * 换机器不用改构建脚本，也就不需要「这台机器上跳过哪些 task」的分支。
 *
 * 工具选择按 [producer] 而非宿主平台：一个格式该走 jpackage 安装器、app-image 目录还是外部
 * appimagetool，是格式自身的属性，不该写成「如果是 macOS 就……」的分支 —— 否则 macOS / Windows
 * 那半边永远没人跑到，腐化了也不知道。
 *
 * @param id 进产物路径的稳定标识，改名会挪动产物路径，故与 [name] 解耦。
 * @param platform 所属平台族，只用于兜底筛选与图标后缀推断。
 * @param jpackageType jpackage 的 `--type` 值；[Producer.APP_IMAGE_TOOL] 没有对应 type。
 * @param producer 这个格式的产物怎么造出来。
 */
enum class BundleFormat(
    val id: String,
    val platform: Platform,
    val jpackageType: String?,
    val producer: Producer,
) {
    APP("app", Platform.DARWIN, "app-image", Producer.APP_IMAGE),
    DMG("dmg", Platform.DARWIN, "dmg", Producer.JPACKAGE_INSTALLER),
    PKG("pkg", Platform.DARWIN, "pkg", Producer.JPACKAGE_INSTALLER),
    EXE("exe", Platform.WINDOWS, "exe", Producer.JPACKAGE_INSTALLER),
    MSI("msi", Platform.WINDOWS, "msi", Producer.JPACKAGE_INSTALLER),
    APP_IMAGE("appimage", Platform.LINUX, null, Producer.APP_IMAGE_TOOL),
    DEB("deb", Platform.LINUX, "deb", Producer.JPACKAGE_INSTALLER),
    RPM("rpm", Platform.LINUX, "rpm", Producer.JPACKAGE_INSTALLER),
    ;

    /** 格式产物的制造方式。 */
    enum class Producer {
        /** app-image 目录本身就是产物（macOS `.app` 即应用身份）。 */
        APP_IMAGE,

        /** jpackage 的 `--type` 安装器，复用已有 app-image。 */
        JPACKAGE_INSTALLER,

        /** app-image 目录交给外部 `appimagetool` 封装（AppImage）。 */
        APP_IMAGE_TOOL,
    }

    /** 图标文件后缀，按平台推断；`krema.toml` 的 `[bundle].icon` 按此后缀兜底匹配。 */
    fun iconSuffix(): String = platform.iconSuffix()
}

/**
 * 把格式枚举名翻成 `kremaPlatform<格式>` task 名里的形状：`APP_IMAGE` → `kremaPlatformAppImage`。
 *
 * 按枚举名（带 `_` 分隔）推大驼峰，而不是 id：`appimage` 一个词拼不出「AppImage」，
 * 但枚举名 `APP_IMAGE` 分段后能拼出，task 名才对人可读。产物路径仍用 [BundleFormat.id]，与 task 名解耦。
 */
fun kremaPlatformTaskName(format: BundleFormat): String =
    "kremaPlatform" + format.name.split('_').joinToString("") { it.lowercase().replaceFirstChar(Char::uppercase) }
