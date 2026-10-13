/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import top.birthcat.kremaf.ext.gradle.tool.AppImageTool
import java.nio.file.Files
import java.nio.file.Path

/** `appimagetool` 的 PATH 查找：纯函数，不读环境变量，便于断言优先顺序。 */
class AppImageToolTest {
    @Test
    @DisplayName("在给定目录里找到 appimagetool")
    fun findsInPath() {
        val dir = Files.createTempDirectory("krema-ai")
        val tool = dir.resolve("appimagetool")
        Files.writeString(tool, "x")
        tool.toFile().setExecutable(true)

        val found = AppImageTool.findInPaths(listOf(dir))

        assertEquals(tool, found)
    }

    @Test
    @DisplayName("目录里没有就返回 null")
    fun notFoundReturnsNull() {
        val dir = Files.createTempDirectory("krema-ai")

        assertNull(AppImageTool.findInPaths(listOf(dir)))
    }
}
