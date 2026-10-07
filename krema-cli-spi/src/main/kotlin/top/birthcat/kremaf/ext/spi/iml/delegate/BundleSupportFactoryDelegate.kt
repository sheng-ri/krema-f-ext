/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import top.birthcat.kremaf.ext.spi.port.BundleSettings
import top.birthcat.kremaf.ext.spi.port.BundleSupport
import top.birthcat.kremaf.ext.spi.port.BundleSupportFactory
import top.birthcat.kremaf.ext.spi.port.FileSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * 平台 bundler 的取用。
 *
 * **不复用上游代码**，只复用接口形状。原因有两层：
 * 上游 bundler 住在 `krema-cli`，上游刻意不发它到 Central；`krema-core` 里也没有 bundler 类
 * （用 `jar tf` 核过 0.3.2 制品）。
 *
 * 所以这里是**自研实现**，接口签名与上游 `AppBundler` 对齐（`createDevBundle` / `createProductionBundle` /
 * `launch` / `requiresBundleForNativeFeatures`），让将来接上游时改名成本最低。
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

    override fun createDevBundle(settings: BundleSettings): Path {
        val bundleDir = outputDir(settings).resolve("${settings.appName}-dev.app")
        val macOSDir = bundleDir.resolve("Contents/MacOS")
        val resourcesDir = bundleDir.resolve("Contents/Resources")
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
        val process = ProcessBuilder(launcher.toAbsolutePath().toString()).inheritIO().start()
        return process
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

/** Windows：目录 + `.exe` 启动器。 */
private class WindowsBundleSupport(
    private val fileSystem: FileSystem,
    override val target: String,
) : BundleSupport {
    override val requiresBundleForNativeFeatures: Boolean = false

    override fun createDevBundle(settings: BundleSettings): Path = createProductionBundle(settings)

    override fun createProductionBundle(settings: BundleSettings): Path {
        val bundleDir = outputDir(settings)
        fileSystem.writeText(
            bundleDir.resolve("${settings.appName}.bat"),
            "@echo off\r\njava --enable-native-access=ALL-UNNAMED -cp \"%~dp0java\\*\" ${settings.mainClass} %*\r\n",
        )
        return bundleDir
    }

    override fun launch(
        bundlePath: Path,
        env: Map<String, String>,
    ): Process {
        val launcher = bundlePath.resolve(bundlePath.fileName.toString() + ".bat")
        return ProcessBuilder("cmd", "/c", launcher.toAbsolutePath().toString()).inheritIO().start()
    }

    private fun outputDir(settings: BundleSettings): Path = Files.createDirectories(settings.outputDir)
}

/** Linux：AppDir 结构，`AppRun` 是入口。 */
private class LinuxBundleSupport(
    private val fileSystem: FileSystem,
    override val target: String,
) : BundleSupport {
    override val requiresBundleForNativeFeatures: Boolean = false

    override fun createDevBundle(settings: BundleSettings): Path = createProductionBundle(settings)

    override fun createProductionBundle(settings: BundleSettings): Path {
        val appDir = outputDir(settings).resolve("${settings.appName}.AppDir")
        val shareDir = appDir.resolve("usr/share/${settings.appName}/java")
        fileSystem.writeText(appDir.resolve("AppRun"), "#!/bin/bash\nexec \"\$(dirname \"\$0\")/usr/bin/${settings.appName}\" \"\$@\"\n")
        fileSystem.markExecutable(appDir.resolve("AppRun"))
        fileSystem.writeText(
            appDir.resolve("usr/bin/${settings.appName}"),
            "#!/bin/bash\nexec java --enable-native-access=ALL-UNNAMED -cp \"$(dirname \"\$0\")/../share/${settings.appName}/java/*\" ${settings.mainClass} \"\$@\"\n",
        )
        fileSystem.markExecutable(appDir.resolve("usr/bin/${settings.appName}"))
        fileSystem.writeText(appDir.resolve("${settings.appName.lowercase()}.desktop"), desktopFile(settings))
        fileSystem.writeText(shareDir.resolve(".keep"), "")
        return appDir
    }

    override fun launch(
        bundlePath: Path,
        env: Map<String, String>,
    ): Process = ProcessBuilder(bundlePath.resolve("AppRun").toAbsolutePath().toString()).inheritIO().start()

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
