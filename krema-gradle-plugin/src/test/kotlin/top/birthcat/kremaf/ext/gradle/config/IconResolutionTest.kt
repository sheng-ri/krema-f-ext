/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import top.birthcat.kremaf.ext.gradle.config.BundleIcons
import top.birthcat.kremaf.ext.gradle.config.resolveBundleIcon
import top.birthcat.kremaf.ext.gradle.domain.Platform
import java.nio.file.Files
import java.nio.file.Path

/** 图标解析：按平台优先扩展图标，toml 兜底按后缀匹配，缺失即跳过。 */
class IconResolutionTest {
    private fun file(name: String): Path {
        val dir = Files.createTempDirectory("krema-icon")
        return Files.writeString(dir.resolve(name), "x")
    }

    @Test
    @DisplayName("扩展图标存在时优先于 toml 兜底")
    fun extIconWins() {
        val mac = file("mac.icns")
        val toml = file("fallback.png")

        val icon = resolveBundleIcon(Platform.DARWIN, BundleIcons(mac = mac, toml = toml))

        assertEquals(mac, icon)
    }

    @Test
    @DisplayName("扩展图标文件不存在则退到 toml 按后缀匹配")
    fun missingExtFallsBackToToml() {
        val toml = file("fallback.icns")

        val icon = resolveBundleIcon(Platform.DARWIN, BundleIcons(toml = toml))

        assertEquals(toml, icon)
    }

    @Test
    @DisplayName("toml 图标后缀与宿主平台不符则跳过")
    fun wrongSuffixSkipped() {
        val toml = file("fallback.png")

        val icon = resolveBundleIcon(Platform.DARWIN, BundleIcons(toml = toml))

        assertNull(icon, "macOS 上 .png 兜底应当被跳过")
    }

    @Test
    @DisplayName("三个图标都缺失且无 toml 时不报错返回 null")
    fun noIconReturnsNull() {
        assertNull(resolveBundleIcon(Platform.LINUX, BundleIcons()))
    }

    @Test
    @DisplayName("Windows 取 winIcon 并按 ico 后缀兜底")
    fun windowsIcon() {
        val win = file("win.ico")
        assertEquals(win, resolveBundleIcon(Platform.WINDOWS, BundleIcons(windows = win)))
        assertNull(resolveBundleIcon(Platform.WINDOWS, BundleIcons(toml = file("x.png"))))
    }
}
