/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.tool

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** jdeps 输出解析：成功时第一行即模块清单。 */
class JdepsParseTest {
    @Test
    @DisplayName("取第一行非空作为模块清单")
    fun firstLineIsModules() {
        val out = "java.base,java.desktop,java.net.http\nwarning: something\n"
        assertEquals("java.base,java.desktop,java.net.http", jdepsModulesFrom(out))
    }

    @Test
    @DisplayName("空输出返回空串")
    fun emptyReturnsBlank() {
        assertEquals("", jdepsModulesFrom("   \n"))
    }
}
