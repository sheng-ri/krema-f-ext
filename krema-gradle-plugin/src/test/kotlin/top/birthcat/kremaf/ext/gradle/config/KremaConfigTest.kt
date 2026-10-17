/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files

/** `krema.toml` 解析与缺省：不依赖 Gradle 注入的纯逻辑。 */
class KremaConfigTest {
    @Test
    @DisplayName("缺文件时必填项全为空")
    fun defaultsWhenAbsent() {
        val dir = Files.createTempDirectory("krema-config")
        val config = KremaConfigLoader().loadOrDefault(dir.resolve("missing.toml"))

        assertNull(config.packageName)
        assertNull(config.version)
        assertNull(config.identifier)
        assertNull(config.build.frontendOutDir)
        assertNull(config.build.mainClass)
        assertNull(config.build.devUrl)
        assertNull(config.frontend.buildCommand)
        assertNull(config.bundle.icon)
    }

    @Test
    @DisplayName("解析出 main_class 与 dev_url")
    fun parsesBuildKeys() {
        val dir = Files.createTempDirectory("krema-config")
        val toml = dir.resolve("krema.toml")
        Files.writeString(
            toml,
            """
            [package]
            name = "demo"
            identifier = "com.example.demo"

            [build]
            main_class = "com.example.demo.Main"
            dev_url = "http://localhost:9000"
            """.trimIndent(),
        )

        val config = KremaConfigLoader().loadOrDefault(toml)

        assertEquals("com.example.demo.Main", config.build.mainClass)
        assertEquals("http://localhost:9000", config.build.devUrl)
        assertEquals("demo", config.packageName)
    }

    @Test
    @DisplayName("[bundle].icon 解析为路径兜底")
    fun parsesBundleIcon() {
        val dir = Files.createTempDirectory("krema-config")
        val toml = dir.resolve("krema.toml")
        Files.writeString(toml, "[bundle]\nicon = \"icon.png\"\n")

        val config = KremaConfigLoader().loadOrDefault(toml)

        assertEquals("icon.png", config.bundle.icon)
    }

    @Test
    @DisplayName("TOML 语法错误抛错并带路径")
    fun throwsOnBadToml() {
        val dir = Files.createTempDirectory("krema-config")
        val toml = dir.resolve("krema.toml")
        Files.writeString(toml, "[package\nname = \"demo\"\n")

        val error =
            assertThrows(IllegalStateException::class.java) {
                KremaConfigLoader().loadOrDefault(toml)
            }

        assertTrue(error.message!!.contains(toml.toString()), "报错应指到文件路径：${error.message}")
    }

    @Test
    @DisplayName("必填缺失时取值抛错")
    fun requiredThrowsWhenMissing() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                KremaConfig().packageName.required(PACKAGE_NAME_HINT)
            }

        assertTrue(error.message!!.contains("package.name"), "报错应指到配置项：${error.message}")
    }
}
