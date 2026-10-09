/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import java.nio.file.Files
import java.nio.file.Path

/**
 * 一个目标平台上的 bundler。
 *
 * 平台差异收在一个类里按 [Platform] 分派，不再拆成接口加三份实现：Krema 支持的平台族是写死的三个，
 * 没有第二个宿主会来换掉其中一份。
 *
 * @param target 目标平台标识，形如 `darwin-aarch64`、`windows-x86_64`、`linux-x86_64`
 */
class PlatformBundler(
    private val target: String,
) {
    private val platform = Platform.of(target.substringBefore('-'))

    /**
     * 该平台是否必须有 bundle 才能用原生特性。
     *
     * 这是 dev 档出不出现 bundle 的唯一依据：macOS 上应用的原生特性认 `.app` 身份，
     * 所以 dev 档也得给一个正经 `.app`，其余平台 dev 档只要最小启动器。
     */
    val requiresBundleForNativeFeatures: Boolean
        get() = platform == Platform.DARWIN

    /** 生成 dev 档产物：需要 bundle 身份的平台出 `<app>-dev.app`，其余平台与 prod 同形。 */
    fun createDevBundle(settings: BundleSettings): Path =
        when (platform) {
            Platform.DARWIN -> createApp(settings, dev = true)
            Platform.WINDOWS, Platform.LINUX -> createProductionBundle(settings)
            Platform.UNKNOWN -> throw UnsupportedOperationException("无法为 '$target' 选择 bundler")
        }

    /** 生成 prod 档产物：全平台出完整可分发形态。 */
    fun createProductionBundle(settings: BundleSettings): Path =
        when (platform) {
            Platform.DARWIN -> createApp(settings, dev = false)
            Platform.WINDOWS -> createWindows(settings)
            Platform.LINUX -> createLinux(settings)
            Platform.UNKNOWN -> throw UnsupportedOperationException("无法为 '$target' 选择 bundler")
        }

    /** 通过 bundle 启动应用进程。 */
    fun launch(
        bundlePath: Path,
        env: Map<String, String>,
    ): Process =
        when (platform) {
            Platform.DARWIN -> {
                ProcessBuilder(
                    bundlePath
                        .resolve("Contents/MacOS")
                        .resolve(bundlePath.fileName.toString().removeSuffix(".app"))
                        .toAbsolutePath()
                        .toString(),
                )
            }

            Platform.WINDOWS -> {
                ProcessBuilder(
                    "cmd",
                    "/c",
                    bundlePath.resolve(bundlePath.fileName.toString() + ".bat").toAbsolutePath().toString(),
                )
            }

            Platform.LINUX -> {
                ProcessBuilder(bundlePath.resolve("AppRun").toAbsolutePath().toString())
            }

            Platform.UNKNOWN -> {
                throw UnsupportedOperationException("无法为 '$target' 选择 bundler")
            }
        }.apply { environment().putAll(env) }.inheritIO().start()

    private fun createApp(
        settings: BundleSettings,
        dev: Boolean,
    ): Path {
        val suffix = if (dev) "-dev" else ""
        val bundleDir = outputDir(settings).resolve("${settings.appName}$suffix.app")
        val macOSDir = bundleDir.resolve("Contents/MacOS")
        FileOps.writeText(bundleDir.resolve("Contents/Info.plist"), infoPlist(settings, dev))
        FileOps.writeText(macOSDir.resolve(settings.appName), launcher(settings, dev))
        FileOps.markExecutable(macOSDir.resolve(settings.appName))
        return bundleDir
    }

    private fun createWindows(settings: BundleSettings): Path {
        val bundleDir = outputDir(settings)
        FileOps.writeText(
            bundleDir.resolve("${settings.appName}.bat"),
            "@echo off\r\njava --enable-native-access=ALL-UNNAMED -cp \"%~dp0java\\*\" ${settings.mainClass} %*\r\n",
        )
        return bundleDir
    }

    private fun createLinux(settings: BundleSettings): Path {
        val appDir = outputDir(settings).resolve("${settings.appName}.AppDir")
        val shareDir = appDir.resolve("usr/share/${settings.appName}/java")
        FileOps.writeText(appDir.resolve("AppRun"), "#!/bin/bash\nexec \"\$(dirname \"\$0\")/usr/bin/${settings.appName}\" \"\$@\"\n")
        FileOps.markExecutable(appDir.resolve("AppRun"))
        FileOps.writeText(
            appDir.resolve("usr/bin/${settings.appName}"),
            "#!/bin/bash\nexec java --enable-native-access=ALL-UNNAMED -cp \"$(dirname \"\$0\")/../share/${settings.appName}/java/*\" ${settings.mainClass} \"\$@\"\n",
        )
        FileOps.markExecutable(appDir.resolve("usr/bin/${settings.appName}"))
        FileOps.writeText(appDir.resolve("${settings.appName.lowercase()}.desktop"), desktopFile(settings))
        FileOps.writeText(shareDir.resolve(".keep"), "")
        return appDir
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

/** 打包输入：产物名、图标与深链。 */
data class BundleSettings(
    val appName: String = "krema-app",
    val outputDir: Path = Path.of("."),
    val identifier: String = "com.example.app",
    val version: String = "0.1.0",
    val mainClass: String = "com.example.app.Main",
    val icon: Path? = null,
    val deepLinkSchemes: List<String> = emptyList(),
)
