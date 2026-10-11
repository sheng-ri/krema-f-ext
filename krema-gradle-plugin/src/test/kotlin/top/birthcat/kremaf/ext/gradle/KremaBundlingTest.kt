/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

/** 平台包格式：`BundleFormat` 枚举与 `krema.bundling { }` 的宿主平台筛选。 */
class KremaBundlingTest : KremaTestBase() {
    /** 报告宿主平台与筛后平台包的 task 名。 */
    private val reportTask = "reportPlatformFormats"

    /** 三平台的格式各写一个，用于验证只跑宿主平台那个属性里的格式。 */
    private val allFormats =
        """
        macosFormats = setOf(BundleFormat.DMG)
        windowsFormats = setOf(BundleFormat.MSI)
        linuxFormats = setOf(BundleFormat.APP_IMAGE, BundleFormat.DEB)
        """

    @Test
    @DisplayName("每个平台包格式映射到所属平台与 jpackage type")
    fun formatsMapToPlatformAndType() {
        val expected =
            mapOf(
                "app" to (Platform.DARWIN to "app-image"),
                "dmg" to (Platform.DARWIN to "dmg"),
                "pkg" to (Platform.DARWIN to "pkg"),
                "exe" to (Platform.WINDOWS to "exe"),
                "msi" to (Platform.WINDOWS to "msi"),
                "appimage" to (Platform.LINUX to null),
                "deb" to (Platform.LINUX to "deb"),
                "rpm" to (Platform.LINUX to "rpm"),
            )

        assertEquals(
            expected.keys.sorted(),
            BundleFormat.entries.map { it.id }.sorted(),
            "平台包格式清单与 GLOSSARY 的「平台包」词条不一致",
        )
        BundleFormat.entries.forEach {
            assertEquals(expected.getValue(it.id), it.platform to it.jpackageType, "格式 $it 的平台或 jpackage type 不对")
        }
    }

    @Test
    @DisplayName("bundling 只取宿主平台的平台包子集")
    fun bundlingKeepsOnlyHostFormats(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), bundlingGradle(allFormats))
        val expected =
            mapOf(
                Platform.DARWIN to listOf("dmg:DARWIN"),
                Platform.WINDOWS to listOf("msi:WINDOWS"),
                Platform.LINUX to listOf("appimage:LINUX", "deb:LINUX"),
            )

        val result = runGradle(projectDir, reportTask)

        assertEquals(TaskOutcome.SUCCESS, result.task(":$reportTask")?.outcome, result.output)
        val picked =
            result.output
                .substringAfter("formats=", "")
                .substringBefore('\n')
                .trim()
                .split(",")
                .filter { it.isNotEmpty() }
        assertEquals(
            expected[PlatformDetector.detect()].orEmpty(),
            picked,
            "跨平台写的格式列表应当只留下宿主平台的子集：\n${result.output}",
        )
    }

    @Test
    @DisplayName("平台包格式不配置就不出包也不报错")
    fun emptyFormatsSkipBundles(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), bundlingGradle(""))

        val result = runGradle(projectDir, reportTask)

        assertEquals(TaskOutcome.SUCCESS, result.task(":$reportTask")?.outcome, result.output)
        val picked =
            result.output
                .substringAfter("formats=", "")
                .substringBefore('\n')
                .trim()
        assertEquals("", picked, "默认空集合时不该出任何平台包：\n${result.output}")
    }

    /**
     * 写一份带报告 task 的 `build.gradle.kts`。
     *
     * [formats] 为空串时不写 `bundling { }` 块，用的是默认空集合 —— 空集合下 `.get()` 不炸
     * 正是那条用例要守的。
     */
    private fun bundlingGradle(formats: String) =
        """
        import top.birthcat.kremaf.ext.gradle.BundleFormat
        import top.birthcat.kremaf.ext.gradle.PlatformDetector

        plugins { id("top.birthcat.kremaf.ext") }

        ${
            if (formats.isEmpty()) {
                ""
            } else {
                """
                krema {
                    bundling {
                        $formats
                    }
                }
                """.trimIndent()
            }
        }

        tasks.register("$reportTask") {
            val host = PlatformDetector.detect().name
            val picked = krema.bundling.formatsForHost().map { it.id + ":" + it.platform.name }
            doLast {
                println("host=" + host)
                println("formats=" + picked.joinToString(","))
            }
        }
        """.trimIndent()
}
