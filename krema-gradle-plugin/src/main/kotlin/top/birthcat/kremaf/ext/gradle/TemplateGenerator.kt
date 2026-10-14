/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import java.nio.file.Files
import java.nio.file.Path

/**
 * `init` 的项目骨架落盘。
 *
 * 自研：只复刻目录形状与两份必需文件（`krema.toml` + 主类）。
 * 命名规则照搬上游：Java 包路径由 identifier 决定，产物名走小写连字符。
 *
 * 落出的 `krema.toml` 只写本仓库真读的键 —— 写进去没人读的键比不写更容易误导。
 */
class TemplateGenerator {
    /**
     * 生成 `init` 骨架：主类 + `krema.toml` + `.gitignore` 落盘到 [dir]，返回 [dir]。
     */
    fun generate(
        dir: Path,
        settings: InitSettings,
    ): Path {
        val packagePath = settings.javaPackage.replace('.', '/')
        // 主类落在 Gradle 认得的标准主 source set，不造 src-java 这类约定目录。
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
        |        // 开发期读 KREMA_DEV_URL 指到使用方自己的前端 dev server；没设就用内建默认。
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
    /** 应用名，同时是目录名与产物名。 */
    val appName: String,
    /** 包标识，Java 包名由它推导。 */
    val identifier: String,
    /** Java 包名，决定主类文件路径。 */
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
