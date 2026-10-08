/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import top.birthcat.kremaf.ext.spi.port.BundleSettings
import top.birthcat.kremaf.ext.spi.port.BundleSupport
import top.birthcat.kremaf.ext.spi.port.BundleSupportFactory
import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.FileSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * 平台 bundler 的取用。
 *
 * 全自研：接口形状由本模块定，实现也自己写。只做「按平台选实现」。
 */
class BundleSupportFactoryDelegate(
    private val fileSystem: FileSystem,
    private val commandRunner: CommandRunner,
) : BundleSupportFactory {
    override fun create(target: String): BundleSupport =
        when (target.substringBefore('-')) {
            // macOS 手搓 `.app`，不碰 jpackage —— dev 流程硬依赖它，见 #27。
            "darwin" -> MacOSBundleSupport(fileSystem)

            "windows" -> WindowsBundleSupport(fileSystem, commandRunner)

            "linux" -> LinuxBundleSupport(fileSystem, commandRunner)

            else -> throw UnsupportedOperationException("无法为 '$target' 选择 bundler")
        }
}

/** macOS：`.app` 目录，原生特性必须有正经 bundle 标识。 */
private class MacOSBundleSupport(
    private val fileSystem: FileSystem,
) : BundleSupport {
    override val requiresBundleForNativeFeatures: Boolean = true

    override val deploysClasspath: Boolean = true

    override fun createDevBundle(settings: BundleSettings): Path {
        val bundleDir = outputDir(settings).resolve("${settings.appName}-dev.app")
        val macOSDir = bundleDir.resolve("Contents/MacOS")
        fileSystem.writeText(bundleDir.resolve("Contents/Info.plist"), infoPlist(settings, dev = true))
        fileSystem.writeText(macOSDir.resolve(settings.appName), launcher(settings, dev = true))
        fileSystem.markExecutable(macOSDir.resolve(settings.appName))
        return bundleDir
    }

    override fun createProductionBundle(settings: BundleSettings): Path {
        val bundleDir = outputDir(settings).resolve("${settings.appName}.app")
        val macOSDir = bundleDir.resolve("Contents/MacOS")
        fileSystem.writeText(bundleDir.resolve("Contents/Info.plist"), infoPlist(settings, dev = false))
        fileSystem.writeText(macOSDir.resolve(settings.appName), launcher(settings, dev = false))
        fileSystem.markExecutable(macOSDir.resolve(settings.appName))
        return bundleDir
    }

    /**
     * 走 Launch Services（`open -a`）而不是直接 exec 启动器脚本。
     *
     * `open` 起不了子进程环境，环境变量改由 `.krema-env` 中转；启动器脚本 source 它，
     * 所以 [env] 仍然到得了应用。
     */
    override fun launch(
        bundlePath: Path,
        env: Map<String, String>,
    ): Process {
        fileSystem.writeText(bundlePath.resolve("Contents/MacOS/$ENV_FILE"), envExports(env))
        return ProcessBuilder("open", "-a", bundlePath.toAbsolutePath().toString(), "--wait-apps")
            .inheritIO()
            .start()
    }

    private fun infoPlist(
        settings: BundleSettings,
        dev: Boolean,
    ) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
            <key>CFBundleName</key><string>${settings.appName}</string>
            <key>CFBundleIdentifier</key><string>${settings.identifier}</string>
            <key>CFBundleVersion</key><string>${settings.version}</string>
            <key>CFBundleExecutable</key><string>${settings.appName}</string>
            <key>CFBundlePackageType</key><string>APPL</string>
            <key>LSMinimumSystemVersion</key><string>11.0</string>
            <key>KremaDev</key><$dev/>
        </dict>
        </plist>
        """.trimIndent()

    private fun launcher(
        settings: BundleSettings,
        dev: Boolean,
    ): String {
        val resources = if (dev) "../../../.krema/dev" else "Contents/Resources"
        return """
            |#!/bin/bash
            |DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)"
            |# launch 把额外环境变量写在这里，open 起不了子进程环境，只能中转。
            |[ -f "${'$'}DIR/.krema-env" ] && . "${'$'}DIR/.krema-env"
            |export KREMA_MAIN_CLASS="${settings.mainClass}"
            |exec java --enable-native-access=ALL-UNNAMED -cp "${'$'}DIR/$resources/Java/*" "${settings.mainClass}" "${'$'}@"
            |
            """.trimMargin()
    }

    private fun outputDir(settings: BundleSettings): Path = Files.createDirectories(settings.outputDir)
}

/** Windows：jpackage `--type exe`。 */
private class WindowsBundleSupport(
    private val fileSystem: FileSystem,
    commandRunner: CommandRunner,
) : BundleSupport {
    private val jpackage = JpackageRunner(commandRunner, fileSystem)

    override val requiresBundleForNativeFeatures: Boolean = false

    override val deploysClasspath: Boolean = false

    override fun createDevBundle(settings: BundleSettings): Path = createProductionBundle(settings)

    override fun createProductionBundle(settings: BundleSettings): Path =
        jpackage.run(
            settings = settings,
            type = "exe",
            destDir = settings.outputDir.resolve("windows"),
            extraArgs = WINDOWS_INSTALLER_OPTIONS,
        )

    /** `--dest` 落下的是安装器 `$appName-$version.exe`，文件名不固定，按后缀找。 */
    override fun launch(
        bundlePath: Path,
        env: Map<String, String>,
    ): Process =
        ProcessBuilder(executableIn(bundlePath, ".exe").toAbsolutePath().toString())
            .apply {
                environment().putAll(env)
                inheritIO()
            }.start()

    private companion object {
        private val WINDOWS_INSTALLER_OPTIONS = listOf("--win-dir-chooser", "--win-shortcut", "--win-menu")
    }
}

/** Linux：jpackage `--type deb`。 */
private class LinuxBundleSupport(
    private val fileSystem: FileSystem,
    commandRunner: CommandRunner,
) : BundleSupport {
    private val jpackage = JpackageRunner(commandRunner, fileSystem)

    override val requiresBundleForNativeFeatures: Boolean = false

    override val deploysClasspath: Boolean = false

    override fun createDevBundle(settings: BundleSettings): Path = createProductionBundle(settings)

    override fun createProductionBundle(settings: BundleSettings): Path =
        jpackage.run(
            settings = settings,
            type = "deb",
            destDir = settings.outputDir.resolve("linux"),
            extraArgs = LINUX_INSTALLER_OPTIONS,
        )

    /** `createProductionBundle` 产出的是一个 `.deb` 文件，启动要等装完；本轮不接 dev 启动。 */
    override fun launch(
        bundlePath: Path,
        env: Map<String, String>,
    ): Nothing =
        throw UnsupportedOperationException(
            "Linux 产物是 .deb 安装包（$bundlePath），装上之后才谈得上启动 —— 没有可 exec 的 bundle 根目录",
        )

    private companion object {
        private val LINUX_INSTALLER_OPTIONS = listOf("--linux-shortcut")
    }
}

/** 按后缀在目录里找启动器：jpackage 的产物名带版本号（`<name>-<version>.exe`），写死必错。 */
private fun executableIn(
    bundlePath: Path,
    suffix: String,
): Path =
    Files.list(bundlePath).use { files ->
        files
            .filter { it.fileName.toString().endsWith(suffix) }
            .findFirst()
            .orElseThrow { UnsupportedOperationException("$bundlePath 里没有 $suffix 启动器") }
    }

/**
 * 把额外环境变量写成 shell 的 `export` 语句。
 *
 * 给「启动器脚本 + 中转文件」那一路用（macOS），这些平台没有 `ProcessBuilder` 的环境注入。
 */
private fun envExports(env: Map<String, String>): String =
    env.entries.joinToString("") { (key, value) -> "export $key='${value.replace("'", "'\\''")}'\n" }

private const val ENV_FILE = ".krema-env"
