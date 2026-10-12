/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import top.birthcat.kremaf.ext.gradle.config.KremaConfigLoader
import java.nio.file.Files

/** `krema.toml` 解析与缺省：不依赖 Gradle 注入的纯逻辑。 */
class KremaConfigTest {
    @Test
    @DisplayName("缺文件时全走默认值")
    fun defaultsWhenAbsent() {
        val dir = Files.createTempDirectory("krema-config")
        val config = KremaConfigLoader().loadOrDefault(dir.resolve("missing.toml"))

        assertEquals("krema-app", config.packageName)
        assertEquals("0.1.0", config.version)
        assertEquals("com.example.app", config.identifier)
        assertEquals("dist", config.build.outDir)
        assertEquals(null, config.build.mainClass)
        assertEquals(null, config.build.devUrl)
        assertEquals(null, config.bundle.icon)
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
}
