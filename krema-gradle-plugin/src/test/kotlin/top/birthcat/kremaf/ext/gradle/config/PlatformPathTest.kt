/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import top.birthcat.kremaf.ext.gradle.config.BUNDLE_DIR_NAME
import top.birthcat.kremaf.ext.gradle.config.platformOutputDirName

/** 产物路径换算：bundle 与 platform/<id> 可预测，上传规则能写死。 */
class PlatformPathTest {
    @Test
    @DisplayName("bundle 目录固定为 build/krema/bundle")
    fun bundleDirName() {
        assertEquals("bundle", BUNDLE_DIR_NAME)
    }

    @Test
    @DisplayName("平台包目录为 platform/<format id>")
    fun platformDirName() {
        assertEquals("platform/appimage", platformOutputDirName("appimage"))
        assertEquals("platform/deb", platformOutputDirName("deb"))
    }
}
