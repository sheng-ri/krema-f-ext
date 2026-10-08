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
            "darwin" -> MacOSBundleSupport(fileSystem, target)

            "windows" -> WindowsBundleSupport(fileSystem, target, commandRunner)

            "linux" -> LinuxBundleSupport(fileSystem, target, commandRunner)

            else -> throw UnsupportedOperationException("无法为 '$target' 选择 bundler")
        }
}

/** macOS：`.app` 目录，原生特性必须有正经 bundle 标识。 */
private class MacOSBundleSupport(
    private val fileSystem: FileSystem,
    override val target: String,
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

    override fun launch(
        bundlePath: Path,
        env: Map<String, String>,
    ): Process {
        val launcher = bundlePath.resolve("Contents/MacOS").resolve(bundlePath.fileName.toString().removeSuffix(".app"))
        return ProcessBuilder(launcher.toAbsolutePath().toString()).inheritIO().start()
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
    override val target: String,
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

    override fun launch(
        bundlePath: Path,
        env: Map<String, String>,
    ): Process {
        val launcher = bundlePath.resolve(bundlePath.fileName.toString() + ".exe")
        return ProcessBuilder(launcher.toAbsolutePath().toString()).inheritIO().start()
    }

    private companion object {
        private val WINDOWS_INSTALLER_OPTIONS = listOf("--win-dir-chooser", "--win-shortcut", "--win-menu")
    }
}

/** Linux：jpackage `--type deb`。 */
private class LinuxBundleSupport(
    private val fileSystem: FileSystem,
    override val target: String,
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

    override fun launch(
        bundlePath: Path,
        env: Map<String, String>,
    ): Process =
        ProcessBuilder(
            bundlePath
                .resolve("bin")
                .resolve(bundlePath.fileName.toString())
                .toAbsolutePath()
                .toString(),
        ).inheritIO()
            .start()

    private companion object {
        private val LINUX_INSTALLER_OPTIONS = listOf("--linux-shortcut")
    }
}
