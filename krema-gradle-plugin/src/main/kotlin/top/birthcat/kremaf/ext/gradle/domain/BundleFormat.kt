/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.domain

import top.birthcat.kremaf.ext.gradle.config.kremaPlatformTaskName

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
 * 构造参数：[id] 进产物路径的稳定标识（与枚举名解耦，改名会挪路径）；[platform] 只用于兜底筛选与图标后缀推断；
 * [jpackageType] 是 jpackage 的 `--type` 值（[Producer.APP_IMAGE_TOOL] 为 null）；[producer] 决定产物怎么造出来。
 */
enum class BundleFormat(
    /** 进产物路径的稳定标识，与枚举名解耦，改名会挪动产物路径。 */
    val id: String,
    /** 所属平台族，只用于兜底筛选与图标后缀推断。 */
    val platform: Platform,
    /** jpackage 的 `--type` 值；[Producer.APP_IMAGE_TOOL] 为 null。 */
    val jpackageType: String?,
    /** 格式产物的制造方式。 */
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

    /** 产物怎么造出来：复用已有 app-image 的三种不同方式，与平台解耦。 */
    enum class Producer {
        /** app-image 目录本身就是产物（macOS `.app` 即应用身份）。 */
        APP_IMAGE,

        /** jpackage 的 `--type` 安装器，复用已有 app-image。 */
        JPACKAGE_INSTALLER,

        /** app-image 目录交给外部 `appimagetool` 封装（AppImage）。 */
        APP_IMAGE_TOOL,
    }

    internal val kremaPlatformTaskName get() = kremaPlatformTaskName(name)
}
