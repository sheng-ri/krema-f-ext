/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import top.birthcat.kremaf.ext.gradle.domain.BundleFormat
import top.birthcat.kremaf.ext.gradle.domain.kremaPlatformTaskName

/** `BundleFormat` 的 producer 与图标后缀映射：决定工具选择，不应按平台写分支。 */
class BundleFormatTest {
    @Test
    @DisplayName("每个格式映射到 producer 与 jpackage type")
    fun mapsProducerAndType() {
        val expected =
            mapOf(
                "app" to (BundleFormat.Producer.APP_IMAGE to "app-image"),
                "dmg" to (BundleFormat.Producer.JPACKAGE_INSTALLER to "dmg"),
                "pkg" to (BundleFormat.Producer.JPACKAGE_INSTALLER to "pkg"),
                "exe" to (BundleFormat.Producer.JPACKAGE_INSTALLER to "exe"),
                "msi" to (BundleFormat.Producer.JPACKAGE_INSTALLER to "msi"),
                "appimage" to (BundleFormat.Producer.APP_IMAGE_TOOL to null),
                "deb" to (BundleFormat.Producer.JPACKAGE_INSTALLER to "deb"),
                "rpm" to (BundleFormat.Producer.JPACKAGE_INSTALLER to "rpm"),
            )
        BundleFormat.entries.forEach {
            assertEquals(expected.getValue(it.id), it.producer to it.jpackageType, "格式 ${it.id} 的 producer/type 不对")
        }
    }

    @Test
    @DisplayName("图标后缀按平台推断")
    fun iconSuffixByPlatform() {
        assertEquals("icns", BundleFormat.APP.iconSuffix())
        assertEquals("ico", BundleFormat.EXE.iconSuffix())
        assertEquals("png", BundleFormat.APP_IMAGE.iconSuffix())
    }

    @Test
    @DisplayName("格式 id 转 kremaPlatform task 名")
    fun platformTaskName() {
        assertEquals("kremaPlatformAppImage", kremaPlatformTaskName(BundleFormat.APP_IMAGE))
        assertEquals("kremaPlatformDeb", kremaPlatformTaskName(BundleFormat.DEB))
        assertEquals("kremaPlatformApp", kremaPlatformTaskName(BundleFormat.APP))
    }
}
