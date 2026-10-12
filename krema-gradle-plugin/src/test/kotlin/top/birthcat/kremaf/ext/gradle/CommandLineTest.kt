/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.commandLineFor

/** 命令拼装：非 Windows 按空白切分，Windows 借道 `cmd /c`。 */
class CommandLineTest {
    @Test
    @DisplayName("非 Windows 按空白切成 argv")
    fun splitsOnUnix() {
        assertEquals(listOf("npm", "run", "build"), commandLineFor("npm run build", Platform.LINUX))
    }

    @Test
    @DisplayName("Windows 借道 cmd /c")
    fun windowsViaCmd() {
        assertEquals(listOf("cmd", "/c", "npm run build"), commandLineFor("npm run build", Platform.WINDOWS))
    }
}
