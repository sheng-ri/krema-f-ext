/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import top.birthcat.kremaf.ext.spi.port.BundleSettings
import top.birthcat.kremaf.ext.spi.port.BundleSupport
import top.birthcat.kremaf.ext.spi.port.BundleSupportFactory
import top.birthcat.kremaf.ext.spi.port.FileSystem
import top.birthcat.kremaf.ext.spi.port.LaunchSettings
import java.nio.file.Files
import java.nio.file.Path

/**
 * 平台 bundler 的取用。
 *
 * 全自研：接口形状由本模块定，实现也自己写。只做「按平台选实现」。
 */
class BundleSupportFactoryDelegate(
    private val fileSystem: FileSystem,
) : BundleSupportFactory {
    override fun create(target: String): BundleSupport =
        when (target.substringBefore('-')) {
            "darwin" -> MacOSBundleSupport(fileSystem, target)
            "windows" -> WindowsBundleSupport(fileSystem, target)
            "linux" -> LinuxBundleSupport(fileSystem, target)
            else -> throw UnsupportedOperationException("无法为 '$target' 选择 bundler")
        }
}

/** macOS：`.app` 目录，原生特性必须有正经 bundle 标识。 */
private class MacOSBundleSupport(
    private val fileSystem: FileSystem,
    override val target: String,
) : BundleSupport {
    override val requiresBundleForNativeFeatures: Boolean = true

    override fun createBundle(
        settings: BundleSettings,
        dev: Boolean,
    ): Path {
        val bundleDir = outputDir(settings).resolve(bundleName(settings, dev))
        val macOSDir = bundleDir.resolve("Contents/MacOS")
        fileSystem.writeText(bundleDir.resolve("Contents/Info.plist"), infoPlist(settings, dev))
        fileSystem.writeText(macOSDir.resolve(settings.appName), launcher(settings, dev))
        fileSystem.markExecutable(macOSDir.resolve(settings.appName))
        return bundleDir
    }

    /** 直接 exec `.app` 里的启动器，不起 `open` —— 多一个 `open` 进程会把 stdout 管道截断，握手判不到。 */
    override fun run(settings: LaunchSettings): Process = processFor(launcherCommand(settings), settings)

    private fun bundleName(
        settings: BundleSettings,
        dev: Boolean,
    ): String = if (dev) "${settings.appName}-dev.app" else "${settings.appName}.app"

    private fun launcherCommand(settings: LaunchSettings): List<String> {
        val launcher =
            settings.bundlePath
                .resolve("Contents/MacOS")
                .resolve(
                    settings.bundlePath.fileName
                        .toString()
                        .removeSuffix(".app")
                        .removeSuffix("-dev"),
                )
        return listOf(launcher.toAbsolutePath().toString())
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
            |exec java --enable-native-access=ALL-UNNAMED -cp "${'$'}DIR/$resources/Java/*:${'$'}DIR/$resources/Java/classes" "${settings.mainClass}" "${'$'}@"
            |
            """.trimMargin()
    }

    private fun outputDir(settings: BundleSettings): Path = Files.createDirectories(settings.outputDir)
}

/** Windows：目录 + `.bat` 启动器。 */
private class WindowsBundleSupport(
    private val fileSystem: FileSystem,
    override val target: String,
) : BundleSupport {
    override val requiresBundleForNativeFeatures: Boolean = false

    override fun createBundle(
        settings: BundleSettings,
        dev: Boolean,
    ): Path {
        val bundleDir = outputDir(settings)
        fileSystem.writeText(
            bundleDir.resolve("${settings.appName}.bat"),
            "@echo off\r\njava --enable-native-access=ALL-UNNAMED -cp \"%~dp0java\\*;%~dp0java\\classes\" ${settings.mainClass} %*\r\n",
        )
        return bundleDir
    }

    override fun run(settings: LaunchSettings): Process {
        val launcher = settings.bundlePath.resolve(settings.bundlePath.fileName.toString() + ".bat")
        return processFor(listOf("cmd", "/c", launcher.toAbsolutePath().toString()), settings)
    }

    private fun outputDir(settings: BundleSettings): Path = Files.createDirectories(settings.outputDir)
}

/** Linux：AppDir 结构，`AppRun` 是入口。 */
private class LinuxBundleSupport(
    private val fileSystem: FileSystem,
    override val target: String,
) : BundleSupport {
    override val requiresBundleForNativeFeatures: Boolean = false

    override fun createBundle(
        settings: BundleSettings,
        dev: Boolean,
    ): Path {
        val appDir = outputDir(settings).resolve("${settings.appName}.AppDir")
        val shareDir = appDir.resolve("usr/share/${settings.appName}/java")
        fileSystem.writeText(appDir.resolve("AppRun"), "#!/bin/bash\nexec \"\$(dirname \"\$0\")/usr/bin/${settings.appName}\" \"\$@\"\n")
        fileSystem.markExecutable(appDir.resolve("AppRun"))
        fileSystem.writeText(
            appDir.resolve("usr/bin/${settings.appName}"),
            "#!/bin/bash\nexec java --enable-native-access=ALL-UNNAMED -cp \"$(dirname \"\$0\")/../share/${settings.appName}/java/*:$(dirname \"\$0\")/../share/${settings.appName}/java/classes\" ${settings.mainClass} \"\$@\"\n",
        )
        fileSystem.markExecutable(appDir.resolve("usr/bin/${settings.appName}"))
        fileSystem.writeText(appDir.resolve("${settings.appName.lowercase()}.desktop"), desktopFile(settings))
        fileSystem.writeText(shareDir.resolve(".keep"), "")
        return appDir
    }

    override fun run(settings: LaunchSettings): Process =
        processFor(
            listOf(
                settings.bundlePath
                    .resolve("AppRun")
                    .toAbsolutePath()
                    .toString(),
            ),
            settings,
        )

    private fun desktopFile(settings: BundleSettings) =
        """
        [Desktop Entry]
        Name=${settings.appName}
        Exec=${settings.appName}${if (settings.deepLinkSchemes.isEmpty()) "" else " %u"}
        Type=Application
        Terminal=false
        Categories=Utility;
        """.trimIndent()

    private fun outputDir(settings: BundleSettings): Path = Files.createDirectories(settings.outputDir)
}
