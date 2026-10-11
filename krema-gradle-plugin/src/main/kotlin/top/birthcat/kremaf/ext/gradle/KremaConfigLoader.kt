/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import com.moandjiezana.toml.Toml
import java.nio.file.Files
import java.nio.file.Path

/**
 * `krema.toml` 的读取与解析。
 *
 * 自研：只复刻配置形状 —— 键名与默认值对齐上游 `krema-cli` 的 `KremaConfig`，解析器用 `com.moandjiezana.toml:toml4j`。
 * 解析失败或文件缺失时退回默认值，与上游 `loadOrDefault` 行为一致。
 */
class KremaConfigLoader {
    /**
     * 读配置文件，解析失败时退回默认值。
     *
     * @param path `krema.toml` 路径
     * @return 解析结果
     */
    fun loadOrDefault(path: Path): KremaConfig {
        if (!Files.isRegularFile(path)) return KremaConfig()
        val toml = runCatching { Toml().read(path.toFile()) }.getOrNull() ?: return KremaConfig()
        return fromToml(toml)
    }

    private fun fromToml(toml: Toml): KremaConfig {
        val pkg = toml.getTable("package")
        val build = toml.getTable("build")
        val bundle = toml.getTable("bundle")
        val deepLink = toml.getTable("deep-link")

        return KremaConfig(
            packageName = pkg?.getString("name", "krema-app") ?: "krema-app",
            version = pkg?.getString("version", "0.1.0") ?: "0.1.0",
            identifier = pkg?.getString("identifier", "com.example.app") ?: "com.example.app",
            build = fromBuild(build),
            frontend = fromFrontend(build),
            bundle = fromBundle(bundle),
            deepLinkSchemes = deepLink?.getList<String>("schemes") ?: emptyList(),
        )
    }

    private fun fromBuild(toml: Toml?): Build =
        Build(
            outDir = toml?.getString("out_dir", "dist") ?: "dist",
            mainClass = toml?.getString("main_class"),
        )

    /** 前端目录与命令落在 `[build]` 段的 `frontend_*` 键上，但住处是 [Frontend]。 */
    private fun fromFrontend(toml: Toml?): Frontend =
        Frontend(
            dir = toml?.getString("frontend_dir"),
            buildCommand = toml?.getString("frontend_command", "npm run build") ?: "npm run build",
            installCommand = toml?.getString("frontend_install_command"),
        )

    private fun fromBundle(toml: Toml?): Bundle =
        Bundle(
            icon = toml?.getString("icon"),
            identifier = toml?.getString("identifier"),
            copyright = toml?.getString("copyright"),
        )
}
