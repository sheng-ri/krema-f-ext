/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

/**
 * 运行时镜像与jdeps 检视：`kremaRuntimeImage` 裁出最小运行时，`jdepsModules` /
 * `jdepsModulesVerbose` 让使用方看清模块是怎么推导出来的。
 */
class KremaRuntimeImageTest : KremaTestBase() {
    @Test
    @DisplayName("运行时镜像里有可执行 java 且小于全量 JDK")
    fun runtimeImageSlimmerThanJdk(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )
        writeToml(projectDir)
        writeMainJava(projectDir)

        val result = runGradle(projectDir, KremaTaskNames.RUNTIME_IMAGE)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.RUNTIME_IMAGE}")?.outcome, result.output)
        val image = projectDir.toPath().resolve("build/krema/runtime")
        assertTrue(
            // jlink 在 Windows 上出的是 java.exe，写死无扩展名的名字会让这条在 Windows 上恒红。
            image.resolve("bin/java" + exeSuffix()).toFile().canExecute(),
            "运行时镜像里没有可执行的 bin/java：\n${result.output}",
        )
        assertTrue(
            treeSize(image.toFile()) < treeSize(File(System.getProperty("java.home"))),
            "运行时镜像应比全量 JDK 小，否则说明 jlink 没生效：\n${result.output}",
        )
    }

    @Test
    @DisplayName("运行时镜像二次执行命中 UP-TO-DATE")
    fun runtimeImageUpToDate(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )
        writeToml(projectDir)
        writeMainJava(projectDir)

        val first = runGradle(projectDir, KremaTaskNames.RUNTIME_IMAGE)

        assertEquals(TaskOutcome.SUCCESS, first.task(":${KremaTaskNames.RUNTIME_IMAGE}")?.outcome, first.output)

        val second = runGradle(projectDir, KremaTaskNames.RUNTIME_IMAGE)

        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":${KremaTaskNames.RUNTIME_IMAGE}")?.outcome,
            "classpath 输入没变时运行时镜像应当 UP-TO-DATE：\n${second.output}",
        )
    }

    @Test
    @DisplayName("jdeps 检视 task 打出模块清单")
    fun jdepsPrintsModules(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )
        writeToml(projectDir)
        writeMainJava(projectDir)

        val result = runGradle(projectDir, KremaTaskNames.JDEPS_MODULES)

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":${KremaTaskNames.JDEPS_MODULES}")?.outcome,
            result.output,
        )
        assertTrue(
            result.output.contains("运行时模块：java.base"),
            "检视 task 应当打出 jdeps 推导出的模块清单：\n${result.output}",
        )
    }

    @Test
    @DisplayName("jdeps 明细 task 写出报告文件")
    fun jdepsVerboseWritesReport(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )
        writeToml(projectDir)
        writeMainJava(projectDir)

        val result = runGradle(projectDir, KremaTaskNames.JDEPS_MODULES_VERBOSE)

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":${KremaTaskNames.JDEPS_MODULES_VERBOSE}")?.outcome,
            result.output,
        )
        val report = projectDir.toPath().resolve("build/krema/jdeps-verbose.txt").toFile()
        assertTrue(report.isFile, "明细 task 没写出报告文件：\n${result.output}")
        assertTrue(report.length() > 0, "报告文件是空的：\n${result.output}")
    }

    @Test
    @DisplayName("手动模块清单取代 jdeps 推导")
    fun modulesOverrideJdeps(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """
            plugins { id("top.birthcat.kremaf.ext") }

            krema {
                runtime {
                    modules = setOf("java.base", "jdk.crypto.ec")
                }
            }
            """.trimIndent(),
        )
        writeToml(projectDir)
        writeMainJava(projectDir)

        val result = runGradle(projectDir, KremaTaskNames.RUNTIME_IMAGE)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.RUNTIME_IMAGE}")?.outcome, result.output)
        val modules = imageModules(projectDir)
        assertTrue(
            modules.containsAll(listOf("java.base", "jdk.crypto.ec")),
            "显式列的模块应当原样进镜像（实际：$modules）：\n${result.output}",
        )
        // 推导路径会把 java.logging 等一并带上；只列了这两个模块时多出来的就是没被取代掉。
        assertFalse(
            modules.contains("java.logging"),
            "手动清单应当完全取代推导结果，不该再混入推导出来的模块（实际：$modules）",
        )
    }

    /** 目录内所有普通文件的字节数之和，用来比运行时镜像与全量 JDK 的体积。 */
    private fun treeSize(root: File): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** 运行时镜像实际打进去的模块名，从 jlink 写的 `release` 文件里读。 */
    private fun imageModules(projectDir: File): List<String> {
        val line =
            Files
                .readAllLines(projectDir.toPath().resolve("build/krema/runtime/release"))
                .first { it.startsWith("MODULES=") }
        return line
            .removePrefix("MODULES=\"")
            .removeSuffix("\"")
            .split(" ")
            .filter { it.isNotEmpty() }
    }
}
