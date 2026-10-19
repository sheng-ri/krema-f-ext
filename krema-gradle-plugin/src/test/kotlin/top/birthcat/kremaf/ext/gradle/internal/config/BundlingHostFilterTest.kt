/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import top.birthcat.kremaf.ext.gradle.api.BundleFormat
import top.birthcat.kremaf.ext.gradle.api.Platform
import top.birthcat.kremaf.ext.gradle.api.selectHostFormats

/** 宿主平台筛选：跨平台写的格式列表只留宿主子集，笔误格式被静默丢弃。 */
class BundlingHostFilterTest {
    @Test
    @DisplayName("只取宿主平台的属性")
    fun keepsHostOnly() {
        val picked =
            selectHostFormats(
                Platform.LINUX,
                setOf(BundleFormat.DMG),
                setOf(BundleFormat.MSI),
                setOf(BundleFormat.APP_IMAGE, BundleFormat.DEB),
            )

        assertEquals(setOf(BundleFormat.APP_IMAGE, BundleFormat.DEB), picked)
    }

    @Test
    @DisplayName("把 DMG 误写进 windowsFormats 被静默丢掉")
    fun typoFormatDropped() {
        val picked = selectHostFormats(Platform.WINDOWS, setOf(BundleFormat.DMG), setOf(BundleFormat.MSI), emptySet())

        assertEquals(setOf(BundleFormat.MSI), picked, "Windows 子集不该出现 macOS 的 DMG")
    }

    @Test
    @DisplayName("空集合时不报不产")
    fun emptyIsEmpty() {
        assertEquals(emptySet<BundleFormat>(), selectHostFormats(Platform.DARWIN, emptySet(), emptySet(), emptySet()))
    }
}
