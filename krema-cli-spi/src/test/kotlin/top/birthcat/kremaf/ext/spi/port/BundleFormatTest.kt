/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * format 三族分类的判据验证。
 *
 * 重点不是枚举成员有几个，是**判据本身**：装完能不能跑，不按扩展名 ——
 * `app-image` 是 jpackage 产物却是可跑的，`app` 是手搓的也是可跑的。
 */
class BundleFormatTest {
    @Test
    fun `可跑与可分发按装完能不能真跑，不按扩展名`() {
        // 手搓 `.app` 与 jpackage `app-image`：谁打的都一样，能直接跑。
        assertEquals(ProductShape.RUNNABLE, BundleFormat.APP.productShape)
        assertEquals(ProductShape.RUNNABLE, BundleFormat.APP_IMAGE.productShape)

        // 安装器一族：产物本身没有可 exec 的入口。
        val installers = setOf(BundleFormat.EXE, BundleFormat.MSI, BundleFormat.DEB, BundleFormat.RPM, BundleFormat.DMG, BundleFormat.PKG)
        assertEquals(installers, BundleFormat.entries.filter { it.installable }.toSet())
    }

    @Test
    fun `一个格式的可跑替代品都在同一族里`() {
        assertEquals(setOf(BundleFormat.APP_IMAGE, BundleFormat.APP), BundleFormat.DEB.runnableAlternatives)
        assertEquals(setOf(BundleFormat.APP_IMAGE, BundleFormat.APP), BundleFormat.DMG.runnableAlternatives)
        assertTrue(BundleFormat.APP.runnableAlternatives.contains(BundleFormat.APP), "可跑格式自己要算自己的替代品")
    }

    @Test
    fun `词表名与 DSL 拼写一致`() {
        assertEquals(BundleFormat.DEB, BundleFormat.of("deb"))
        assertEquals(BundleFormat.APP_IMAGE, BundleFormat.of("app-image"))
        assertEquals(null, BundleFormat.of("AppImage"), "词表没有 appimage 这个成员（Linux 面待 #35 收口）")
    }

    @Test
    fun `run 遇到安装器格式硬报错，且话说清怎么改`(
        @TempDir root: Path,
    ) {
        val deb = root.resolve("krema-app-1.2.3.deb")

        val thrown =
            assertThrows(UnsupportedOperationException::class.java) {
                requireRunnable(BundleFormat.DEB, deb)
            }

        // 三个要素缺一不可：产物路径、要先装、往哪改。
        assertTrue(thrown.message!!.contains(deb.toString()), thrown.message!!)
        assertTrue(thrown.message!!.contains("装上"), thrown.message!!)
        assertTrue(thrown.message!!.contains("app-image"), thrown.message!!)
    }

    @Test
    fun `run 遇到可跑格式放行`(
        @TempDir root: Path,
    ) {
        requireRunnable(BundleFormat.APP_IMAGE, root.resolve("krema-app"))
        requireRunnable(BundleFormat.APP, root.resolve("krema-app.app"))

        assertFalse(BundleFormat.APP.installable)
    }
}
