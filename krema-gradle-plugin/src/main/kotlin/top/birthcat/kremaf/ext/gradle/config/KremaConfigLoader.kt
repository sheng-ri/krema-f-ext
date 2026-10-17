/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import org.tomlj.Toml
import org.tomlj.TomlTable
import java.nio.file.Files
import java.nio.file.Path

/**
 * `krema.toml` 的读取与解析。
 *
 * 自研：只复刻配置形状 —— 键名与默认值对齐上游 `krema-cli` 的 `KremaConfig`，解析器用 `org.tomlj:toml4j`。
 * 文件缺失时返回空 [KremaConfig]（缺哪项由必填校验报错）；`Toml.parse` 对非法 TOML 永不抛异常，错误落在
 * [org.tomlj.TomlParseResult.hasErrors]，所以要显式检查 —— 否则一个 `[buidle]` 拼写错误会被静默当成「配置全走默认」，
 * 等到打包产出错名字的产物才被发现。
 */
class KremaConfigLoader {
    /**
     * 文件缺失时返回字段全空的 [KremaConfig]；存在但 TOML 非法时抛 [IllegalStateException]，附文件路径与解析错误。
     *
     * @throws IllegalStateException `krema.toml` 语法错误。
     */
    @Throws(IllegalStateException::class)
    fun loadOrDefault(path: Path): KremaConfig {
        if (!Files.isRegularFile(path)) return KremaConfig()
        val config = Toml.parse(path)
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
            build = fromBuild(toml),
            frontend = fromFrontend(toml),
            bundle = fromBundle(toml),
        )

    private fun fromBuild(toml: TomlTable) =
        Build(
            frontendOutDir = toml.getString("build.frontend_out_dir"),
            mainClass = toml.getString("build.main_class"),
            devUrl = toml.getString("build.dev_url"),
        )

    /** 前端目录与命令落在 `[build]` 段的 `frontend_*` 键上，但住处是 [Frontend]。 */
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
}
