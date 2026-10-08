/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * format 三族分类的判据验证。
 *
 * 重点不是枚举有几个成员，是**判据本身**：装完能不能跑，不按扩展名 ——
 * `app-image` 是 jpackage 打的却是可跑的，`app` 是手搓的也是可跑的。
 */
class BundleFormatTest {
    @Test
    fun runnableVsDistributableFollowsInstallabilityNotExtension() {
        assertEquals(ProductShape.RUNNABLE, BundleFormat.APP.productShape)
        assertEquals(ProductShape.RUNNABLE, BundleFormat.APP_IMAGE.productShape)

        val installers =
            setOf(BundleFormat.EXE, BundleFormat.MSI, BundleFormat.DEB, BundleFormat.RPM, BundleFormat.DMG, BundleFormat.PKG)
        assertEquals(installers, BundleFormat.entries.filter { it.installable }.toSet())
    }

    @Test
    fun runnableAlternativesOfInstallerAreTheRunnableFamily() {
        assertEquals(setOf(BundleFormat.APP_IMAGE, BundleFormat.APP), BundleFormat.DEB.runnableAlternatives)
        assertTrue(BundleFormat.APP.runnableAlternatives.contains(BundleFormat.APP), "可跑格式自己算自己的替代品")
    }

    @Test
    fun formatNamesMatchDslSpelling() {
        assertEquals(BundleFormat.DEB, BundleFormat.of("deb"))
        assertEquals(BundleFormat.APP_IMAGE, BundleFormat.of("app-image"))
        assertEquals(BundleFormat.APP_IMAGE, BundleFormat.of("APP_IMAGE"), "下划线与大写都认")
        assertEquals(BundleFormat.APP_IMAGE, BundleFormat.of("  App-Image "), "前后空格与大小写都认")
        assertNull(BundleFormat.of("dmg2"), "词表没收的成员查不到")
    }

    @Test
    fun runFailsLoudlyOnInstallerFormat(
        @TempDir root: Path,
    ) {
        val deb = root.resolve("krema-app-1.2.3.deb")

        val thrown =
            assertThrows(UnsupportedOperationException::class.java) {
                requireRunnable(BundleFormat.DEB, deb)
            }

        // 三要素缺一不可：产物路径、要先装、往哪改。
        assertTrue(thrown.message!!.contains(deb.toString()), thrown.message!!)
        assertTrue(thrown.message!!.contains("装上"), thrown.message!!)
        assertTrue(thrown.message!!.contains("app-image"), thrown.message!!)
    }

    @Test
    fun runAllowsRunnableFormat(
        @TempDir root: Path,
    ) {
        requireRunnable(BundleFormat.APP_IMAGE, root.resolve("krema-app"))
        requireRunnable(BundleFormat.APP, root.resolve("krema-app.app"))
        assertFalse(BundleFormat.APP.installable)
    }
}
