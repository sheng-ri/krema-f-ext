/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import java.nio.file.Path

/**
 * 打包输入：产物名、图标、深链，以及前端产物在 app jar classpath 上的落点。
 *
 * bundle 目录结构与启动器都由 jpackage 生成，这里只留「产物叫什么」这一层命名输入。
 */
data class BundleSettings(
    val appName: String = "krema-app",
    val outputDir: Path = Path.of("."),
    val identifier: String = "com.example.app",
    val version: String = "0.1.0",
    val mainClass: String = "com.example.app.Main",
    val icon: Path? = null,
    val deepLinkSchemes: List<String> = emptyList(),
    val assetsPath: String = "web",
)

/**
 * jpackage 产出的 app-image 里那个可执行启动器的落点。
 *
 * jpackage 已经把启动器按平台惯例摆好了（macOS 在 `Contents/MacOS`，其余平台在 `bin`），
 * 所以这里只做「产物目录 + 平台 → 可执行文件」这层换算，不再自己写启动器。
 *
 * @param target 目标平台标识，形如 `darwin-aarch64`、`windows-x86_64`、`linux-x86_64`
 */
class PlatformBundler(
    private val target: String,
) {
    private val platform = Platform.of(target.substringBefore('-'))

    /** app-image 目录：bundle task 与 run task 都得就这个路径达成一致。 */
    fun appImageDir(settings: BundleSettings): Path = settings.outputDir.resolve(platform.appImageDirName(settings.appName))

    /** app-image 里的启动器可执行文件，run task 起的就是它。 */
    fun launcherPath(settings: BundleSettings): Path {
        val appImage = appImageDir(settings)
        return when (platform) {
            Platform.DARWIN -> appImage.resolve("Contents/MacOS").resolve(settings.appName)
            Platform.WINDOWS -> appImage.resolve("${settings.appName}.exe")
            Platform.LINUX -> appImage.resolve("bin").resolve(settings.appName)
            Platform.UNKNOWN -> throw UnsupportedOperationException("无法为 '$target' 选择启动器")
        }
    }

    /** 通过 app-image 的启动器起应用进程。 */
    fun launch(
        settings: BundleSettings,
        env: Map<String, String>,
    ): Process =
        ProcessBuilder(launcherPath(settings).toAbsolutePath().toString())
            .apply { environment().putAll(env) }
            .inheritIO()
            .start()
}
