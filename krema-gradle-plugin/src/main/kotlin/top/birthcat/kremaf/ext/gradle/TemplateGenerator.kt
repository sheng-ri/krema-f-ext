/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import java.nio.file.Files
import java.nio.file.Path

/** 生成 `init` 项目骨架（目录形状 + 必需文件），只写本仓库真读的 `krema.toml` 键以免误导。 */
class TemplateGenerator {
    fun generate(
        dir: Path,
        settings: InitSettings,
    ): Path {
        val packagePath = settings.javaPackage.replace('.', '/')
        writeText(dir.resolve("src/main/java/$packagePath/Main.java"), mainClass(settings))
        writeText(dir.resolve("krema.toml"), kremaToml(settings))
        writeText(dir.resolve(".gitignore"), GITIGNORE)
        return dir
    }

    private fun writeText(
        path: Path,
        content: String,
    ) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, content)
    }

    private fun mainClass(settings: InitSettings) =
        """
        |package ${settings.javaPackage};
        |
        |import build.krema.core.Krema;
        |import build.krema.core.platform.NativeLibraryLoader
        |
        |public final class Main {
        |    public static void main(String[] args) {
        |        if (System.getProperty("os.name").lowercase().contains("win")) {
        |            NativeLibraryLoader.load("libwinpthread-1")
        |        }
        |        // 开发期读 KREMA_DEV_URL 指到使用方自己的前端 dev server；没就用内建默认。
        |        String devUrl = System.getenv("KREMA_DEV_URL");
        |        Krema.app()
        |            .title("${settings.appName}")
        |            .devUrl(devUrl == null ? "http://localhost:5173" : devUrl)
        |            .prodAssets("/assets")
        |            .run();
        |    }
        |}
        |
        """.trimMargin()

    private fun kremaToml(settings: InitSettings) =
        """
        |[package]
        |name = "${settings.appName}"
        |version = "0.1.0"
        |identifier = "${settings.identifier}"
        |
        |[build]
        |frontend_command = "npm run build"
        |out_dir = "dist"
        |main_class = "${settings.javaPackage}.Main"
        |
        """.trimMargin()

    private companion object {
        private const val GITIGNORE = "build/\n.krema/\ntarget/\ndist/\nnode_modules/\n"
    }
}

/**
 * `init` 的输入：应用名、包标识、Java 包名三者互相推导。
 */
data class InitSettings(
    val appName: String,
    val identifier: String,
    val javaPackage: String,
) {
    companion object {
        /** 按上游 `InteractivePrompter` 的规则从应用名推导默认值。 */
        fun defaults(appName: String = "krema-app"): InitSettings {
            val slug = appName.lowercase().replace(Regex("[^a-z0-9]"), "")
            return InitSettings(
                appName = appName,
                identifier = "com.example.$slug",
                javaPackage = "com.example.$slug",
            )
        }
    }
}
