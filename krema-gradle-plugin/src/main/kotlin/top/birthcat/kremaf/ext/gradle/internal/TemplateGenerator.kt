/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal

import top.birthcat.kremaf.ext.gradle.internal.config.ASSETS_DIR
import top.birthcat.kremaf.ext.gradle.internal.config.DEFAULT_APP_NAME
import java.nio.file.Files
import java.nio.file.Path

/** 生成 init 骨架；只写本仓库真读的 toml 键，避免配了不生效。 */
internal class TemplateGenerator {
    /** 写出骨架文件并返回根目录。 */
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
        |        // In dev, KREMA_DEV_URL points at the caller's own frontend dev server; otherwise use the built-in default.
        |        String devUrl = System.getenv("KREMA_DEV_URL");
        |        Krema.app()
        |            .title("${settings.appName}")
        |            .devUrl(devUrl == null ? "http://localhost:5173" : devUrl)
        |            .prodAssets("$ASSETS_DIR")
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
        |frontend_dev_url = "http://localhost:5173"
        |
        """.trimMargin()

    private companion object {
        private const val GITIGNORE = "build/\n.krema/\ntarget/\ndist/\nnode_modules/\n"
    }
}

/** init 的输入；应用名推导包标识与 Java 包名。 */
internal data class InitSettings(
    val appName: String,
    val identifier: String,
    val javaPackage: String,
) {
    companion object {
        /** 按上游 InteractivePrompter 的规则推导默认值。 */
        fun defaults(appName: String = DEFAULT_APP_NAME) =
            appName.lowercase().replace(Regex("[^a-z0-9]"), "").let { slug ->
                InitSettings(
                    appName = appName,
                    identifier = "com.example.$slug",
                    javaPackage = "com.example.$slug",
                )
            }
    }
}
