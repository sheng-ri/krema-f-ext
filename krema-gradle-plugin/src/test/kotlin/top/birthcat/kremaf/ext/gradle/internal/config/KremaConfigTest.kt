/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.config

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
        assertNull(config.build.outDir)
        assertNull(config.build.mainClass)
        assertNull(config.build.frontendDevUrl)
        assertNull(config.build.devUrl)
        assertNull(config.frontend.buildCommand)
        assertNull(config.bundle.icon)
        assertNull(config.windows.dirChooser)
    }

    @Test
    @DisplayName("解析 [build] 段的键")
    fun parsesBuildKeys() {
        val dir = Files.createTempDirectory("krema-config")
        val toml = dir.resolve("krema.toml")
        Files.writeString(
            toml,
            """
            [package]
            name = "demo"
            identifier = "com.example.demo"
            description = "Demo app"

            [build]
            main_class = "com.example.demo.Main"
            dev_url = "http://localhost:9000"
            frontend_dev_url = "http://localhost:9001"
            out_dir = "out"
            java_source_dir = "src-java"
            """.trimIndent(),
        )

        val config = KremaConfigLoader().loadOrDefault(toml)

        assertEquals("com.example.demo.Main", config.build.mainClass)
        assertEquals("http://localhost:9000", config.build.devUrl)
        assertEquals("http://localhost:9001", config.build.frontendDevUrl)
        assertEquals("out", config.build.outDir)
        assertEquals("src-java", config.build.javaSourceDir)
        assertEquals("demo", config.packageName)
        assertEquals("Demo app", config.description)
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
    @DisplayName("[bundle.windows] 解析安装器参数")
    fun parsesBundleWindows() {
        val dir = Files.createTempDirectory("krema-config")
        val toml = dir.resolve("krema.toml")
        Files.writeString(
            toml,
            """
            [bundle.windows]
            dir_chooser = true
            help_url = "https://example.com/help"
            menu = true
            menu_group = "Demo"
            per_user_install = true
            shortcut = true
            shortcut_prompt = true
            update_url = "https://example.com/update"
            upgrade_uuid = "6f1d2c34-0000-4000-8000-000000000000"
            """.trimIndent(),
        )

        val windows = KremaConfigLoader().loadOrDefault(toml).windows

        assertEquals(
            Windows(
                dirChooser = true,
                helpUrl = "https://example.com/help",
                menu = true,
                menuGroup = "Demo",
                perUserInstall = true,
                shortcut = true,
                shortcutPrompt = true,
                updateUrl = "https://example.com/update",
                upgradeUuid = "6f1d2c34-0000-4000-8000-000000000000",
            ),
            windows,
        )
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
