/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import com.moandjiezana.toml.Toml
import top.birthcat.kremaf.ext.spi.port.Build
import top.birthcat.kremaf.ext.spi.port.Bundle
import top.birthcat.kremaf.ext.spi.port.KremaConfig
import top.birthcat.kremaf.ext.spi.port.MacOSSigning
import top.birthcat.kremaf.ext.spi.port.Window
import top.birthcat.kremaf.ext.spi.port.WindowsSigning
import java.nio.file.Files
import java.nio.file.Path

/**
 * `krema.toml` 的读取与解析。
 *
 * 上游 `build.krema.cli.KremaConfig` 住在 `krema-cli`，不发制品，无法复用。这里只复刻**配置形状**：
 * 键名与默认值对齐上游的解析逻辑，解析器换成同族但独立维护的 `com.moandjiezana.toml:toml4j`。
 *
 * 上游的 `[env.<profile>]` 覆盖逻辑尚未落地，入参先留着，传 null / 空串即不生效。
 */
class KremaConfigLoader {
    /**
     * 读配置文件，解析失败时退回默认值 —— 与上游 `loadOrDefault` 行为一致。
     *
     * @param path `krema.toml` 路径
     * @param profile 环境档位名，暂未生效，留着对齐上游签名
     * @return 解析结果，字段与上游 `KremaConfig` 逐项对应
     */
    fun loadOrDefault(
        path: Path,
        @Suppress("UNUSED_PARAMETER") profile: String? = null,
    ): KremaConfig {
        if (!Files.isRegularFile(path)) return KremaConfig()
        val toml = runCatching { Toml().read(path.toFile()) }.getOrNull() ?: return KremaConfig()
        return fromToml(toml)
    }

    private fun fromToml(toml: Toml): KremaConfig {
        val pkg = toml.getTable("package")
        val window = toml.getTable("window")
        val build = toml.getTable("build")
        val bundle = toml.getTable("bundle")
        val updater = toml.getTable("updater")
        val deepLink = toml.getTable("deep-link")

        return KremaConfig(
            packageName = pkg?.getString("name", "krema-app") ?: "krema-app",
            version = pkg?.getString("version", "0.1.0") ?: "0.1.0",
            identifier = pkg?.getString("identifier", "com.example.app") ?: "com.example.app",
            description = pkg?.getString("description", "") ?: "",
            window = fromWindow(window),
            build = fromBuild(build),
            bundle = fromBundle(bundle),
            permissions = toml.getTable("permissions")?.getList<String>("allow") ?: emptyList(),
            updaterPubkey = updater?.getString("pubkey"),
            deepLinkSchemes = deepLink?.getList<String>("schemes") ?: emptyList(),
        )
    }

    private fun fromWindow(toml: Toml?): Window =
        Window(
            title = toml?.getString("title", "Krema App") ?: "Krema App",
            width = (toml?.getLong("width", 1024L) ?: 1024L).toInt(),
            height = (toml?.getLong("height", 768L) ?: 768L).toInt(),
            minWidth = (toml?.getLong("min_width", 0L) ?: 0L).toInt(),
            minHeight = (toml?.getLong("min_height", 0L) ?: 0L).toInt(),
            resizable = toml?.getBoolean("resizable", true) ?: true,
            fullscreen = toml?.getBoolean("fullscreen", false) ?: false,
            decorations = toml?.getBoolean("decorations", true) ?: true,
        )

    private fun fromBuild(toml: Toml?): Build =
        Build(
            frontendCommand = toml?.getString("frontend_command", "npm run build") ?: "npm run build",
            frontendDevCommand = toml?.getString("frontend_dev_command", "npm run dev") ?: "npm run dev",
            frontendDevUrl =
                toml?.getString("frontend_dev_url", "http://localhost:5173")
                    ?: "http://localhost:5173",
            outDir = toml?.getString("out_dir", "dist") ?: "dist",
            javaSourceDir = toml?.getString("java_source_dir", "src-java") ?: "src-java",
            mainClass = toml?.getString("main_class"),
            assetsPath = toml?.getString("assets_path", "assets") ?: "assets",
        )

    private fun fromBundle(toml: Toml?): Bundle {
        val macOS = toml?.getTable("macos")
        val windows = toml?.getTable("windows")
        return Bundle(
            icon = toml?.getString("icon"),
            identifier = toml?.getString("identifier"),
            copyright = toml?.getString("copyright"),
            macOS =
                MacOSSigning(
                    signingIdentity = macOS?.getString("signing_identity"),
                    entitlements = macOS?.getString("entitlements"),
                    notarizationAppleId = macOS?.getString("notarization_apple_id"),
                    notarizationTeamId = macOS?.getString("notarization_team_id"),
                ),
            windows =
                WindowsSigning(
                    signingCertificate = windows?.getString("signing_certificate"),
                    timestampUrl =
                        windows?.getString("timestamp_url", "http://timestamp.digicert.com")
                            ?: "http://timestamp.digicert.com",
                ),
        )
    }
}
