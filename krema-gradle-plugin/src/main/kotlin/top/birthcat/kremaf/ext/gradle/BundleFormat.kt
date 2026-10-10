/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

/**
 * 平台包格式，即本平台原生的可安装件。
 *
 * 用户在一份全局列表里写出所有想要的格式，构建时插件按 [Platform] 取出宿主平台的子集 ——
 * 换机器不用改构建脚本，也就不需要「这台机器上跳过哪些 task」的分支。
 *
 * @param id 进产物路径的稳定标识，改名会挪动产物路径，故与 [name] 解耦。
 * @param platform 所属平台族。
 * @param jpackageType jpackage 的 `--type` 值；AppImage 没有对应 type，由 app-image + appimagetool 兜。
 */
enum class BundleFormat(
    val id: String,
    val platform: Platform,
    val jpackageType: String?,
) {
    APP("app", Platform.DARWIN, "app-image"),
    DMG("dmg", Platform.DARWIN, "dmg"),
    PKG("pkg", Platform.DARWIN, "pkg"),
    EXE("exe", Platform.WINDOWS, "exe"),
    MSI("msi", Platform.WINDOWS, "msi"),
    APP_IMAGE("appimage", Platform.LINUX, null),
    DEB("deb", Platform.LINUX, "deb"),
    RPM("rpm", Platform.LINUX, "rpm"),
}
