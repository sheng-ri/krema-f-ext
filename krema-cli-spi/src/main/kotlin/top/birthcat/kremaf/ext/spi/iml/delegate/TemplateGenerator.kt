/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import top.birthcat.kremaf.ext.spi.port.FileSystem
import java.nio.file.Path

/**
 * `init` 的项目骨架落盘。
 *
 * 上游 `build.krema.cli.init.TemplateGenerator` 是 1647 行、把整套模板写死在 Java 字面量里的类，且不发制品。
 * 这里只复刻**目录形状与两份必需文件**（`krema.toml` + 主类），把大段模板降级为后续工单 ——
 * 工单里「模板落盘方式」还是 Not yet specified，先把可测的骨架立住。
 *
 * 命名规则照搬上游：Java 包路径由 identifier 决定，产物名走小写连字符。
 */
class TemplateGenerator(
    private val fileSystem: FileSystem,
) {
    /**
     * 生成骨架。
     *
     * @param dir 项目根目录
     * @param settings 生成参数
     * @return `dir`
     */
    fun generate(
        dir: Path,
        settings: InitSettings,
    ): Path {
        val packagePath = settings.javaPackage.replace('.', '/')
        fileSystem.writeText(dir.resolve("src-java/$packagePath/Main.java"), mainClass(settings))
        fileSystem.writeText(dir.resolve("krema.toml"), kremaToml(settings))
        fileSystem.writeText(dir.resolve(".gitignore"), GITIGNORE)
        return dir
    }

    private fun mainClass(settings: InitSettings) =
        """
        |package ${settings.javaPackage};
        |
        |import build.krema.core.api.app.KremaApp;
        |
        |public final class Main {
        |    public static void main(String[] args) {
        |        KremaApp.start(args);
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
        |description = "${settings.description}"
        |
        |[window]
        |title = "${settings.windowTitle}"
        |width = 1024
        |height = 768
        |
        |[build]
        |frontend_command = "npm run build"
        |frontend_dev_command = "npm run dev"
        |frontend_dev_url = "http://localhost:5173"
        |out_dir = "dist"
        |java_source_dir = "src-java"
        |main_class = "${settings.javaPackage}.Main"
        |assets_path = "assets"
        |
        """.trimMargin()

    private companion object {
        private const val GITIGNORE = "build/\n.krema/\ntarget/\ndist/\nnode_modules/\n"
    }
}

/**
 * `init` 的输入。
 *
 * @property appName 应用名，同时是目录名
 * @property identifier 包标识，Java 包名由它推导
 * @property windowTitle 窗口标题
 * @property description 应用描述
 * @property javaPackage Java 包名
 */
data class InitSettings(
    val appName: String,
    val identifier: String,
    val windowTitle: String,
    val description: String,
    val javaPackage: String,
) {
    companion object {
        /** 按上游 `InteractivePrompter` 的规则从应用名推导默认值。 */
        fun defaults(appName: String = "krema-app"): InitSettings {
            val slug = appName.lowercase().replace(Regex("[^a-z0-9]"), "")
            return InitSettings(
                appName = appName,
                identifier = "com.example.$slug",
                windowTitle = appName.replaceFirstChar { it.uppercase() },
                description = "A Krema desktop application",
                javaPackage = "com.example.$slug",
            )
        }
    }
}
