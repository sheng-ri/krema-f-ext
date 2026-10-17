/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.config

import org.tomlj.Toml
import org.tomlj.TomlTable
import java.nio.file.Files
import java.nio.file.Path

/** krema.toml 的读取与解析；语法错误显式报错，不静默当成默认配置。 */
internal class KremaConfigLoader {
    /** 文件缺失返回空配置；语法错误抛 [IllegalStateException] 并带路径。 */
    @Throws(IllegalStateException::class)
    fun loadOrDefault(path: Path): KremaConfig {
        if (!Files.isRegularFile(path)) return KremaConfig()
        val config = Toml.parse(path)
        // Toml.parse 不抛异常，错误落在 hasErrors，必须显式检查。
        if (config.hasErrors()) {
            val detail = config.errors().joinToString("; ") { it.toString() }
            throw IllegalStateException("krema.toml syntax error: $path ($detail)")
        }
        return fromToml(config)
    }

    private fun fromToml(toml: TomlTable) =
        KremaConfig(
            packageName = toml.getString("package.name"),
            version = toml.getString("package.version"),
            identifier = toml.getString("package.identifier"),
            description = toml.getString("package.description"),
            build = fromBuild(toml),
            frontend = fromFrontend(toml),
            bundle = fromBundle(toml),
            windows = fromWindows(toml),
        )

    private fun fromBuild(toml: TomlTable) =
        Build(
            frontendOutDir = toml.getString("build.frontend_out_dir"),
            outDir = toml.getString("build.out_dir"),
            javaSourceDir = toml.getString("build.java_source_dir"),
            mainClass = toml.getString("build.main_class"),
            frontendDevUrl = toml.getString("build.frontend_dev_url"),
            devUrl = toml.getString("build.dev_url"),
        )

    /** 前端键在 [build] 段，但归入 [Frontend]。 */
    private fun fromFrontend(toml: TomlTable) =
        Frontend(
            dir = toml.getString("build.frontend_dir"),
            buildCommand = toml.getString("build.frontend_command"),
            installCommand = toml.getString("build.frontend_install_command"),
        )

    private fun fromBundle(toml: TomlTable) =
        Bundle(
            icon = toml.getString("bundle.icon"),
            identifier = toml.getString("bundle.identifier"),
            copyright = toml.getString("bundle.copyright"),
        )

    /** 键名去掉 win_ 前缀：段名已表达归属，与 frontend_* 的处理一致。 */
    private fun fromWindows(toml: TomlTable) =
        Windows(
            dirChooser = toml.getBoolean("bundle.windows.dir_chooser"),
            helpUrl = toml.getString("bundle.windows.help_url"),
            menu = toml.getBoolean("bundle.windows.menu"),
            menuGroup = toml.getString("bundle.windows.menu_group"),
            perUserInstall = toml.getBoolean("bundle.windows.per_user_install"),
            shortcut = toml.getBoolean("bundle.windows.shortcut"),
            shortcutPrompt = toml.getBoolean("bundle.windows.shortcut_prompt"),
            updateUrl = toml.getString("bundle.windows.update_url"),
            upgradeUuid = toml.getString("bundle.windows.upgrade_uuid"),
        )
}
