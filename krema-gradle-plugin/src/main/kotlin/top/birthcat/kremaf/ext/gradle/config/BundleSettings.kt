/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.appImageDirName
import java.nio.file.Path

// 打包输入：产物名、图标、深链，以及前端产物在 app jar classpath 上的落点。
//
// bundle 目录结构与启动器都由 jpackage 生成，这里只留「产物叫什么」这一层命名输入。
data class BundleSettings(
    val appName: String = "krema-app",
    val outputDir: Path = Path.of("."),
    val identifier: String = "com.example.app",
    val version: String = "0.1.0",
    val mainClass: String = "com.example.app.Main",
    val icon: Path? = null,
    val deepLinkSchemes: List<String> = emptyList(),
)

// 前端产物在 assets jar 里的目录名。
//
// 固定为 `assets` 且不开放配置：Krema 主类用 `prodAssets("/assets")` 按这个约定读，
// 两边必须字面一致。多一个可配项就多一处能配歪的地方，而这里没有任何正当的分歧理由。
//
// 前端是「整个应用」的资源，由 `kremaBuild` 打成独立的 assets jar 落到 classpath 上，
// 而不是合并进 app jar：`prodAssets("/assets")` 经 `ClassLoader.getResourceAsStream` 扫整个 classpath，
// 独立 jar 里的 `assets/*` 同样能被读到，且后端 app jar 得以保持纯粹。
const val ASSETS_DIR: String = "assets"

// jar manifest 的约定路径，打包时先写一份，资源目录同名文件才不会被写第二遍。
const val MANIFEST_PATH: String = "META-INF/MANIFEST.MF"

// krema 产物根下 bundle（app-image）与 platform（各格式）两层的固定目录名，CI 上传规则照这个写死。
const val BUNDLE_DIR_NAME: String = "bundle"

/**
 * 平台包产物目录名（相对 krema 产物根）：`platform/<format id>`。
 *
 * 与 [BundleFormat.id] 解耦拼写：改 id 即挪路径，这里只拼层级。单测核对路径可预测性。
 */
fun platformOutputDirName(formatId: String): String = "platform/$formatId"

/**
 * 解析宿主平台的图标路径。
 *
 * 三个按平台的扩展图标优先；都没配（或文件不存在）时才退到 `[bundle].icon`，并按宿主平台后缀
 * （icns/ico/png）匹配 —— 对不上就跳过。都缺失则产物不带 `--icon`，不报错。
 *
 * 纯逻辑、不碰 Gradle 注入：单测能直接覆盖「笔误后缀被静默丢弃」「扩展图标文件不存在即跳过」两条。
 *
 * @param platform 宿主平台
 * @param macIcon `macIcon` 扩展值（绝对路径），可能为 null
 * @param windowsIcon `winIcon` 扩展值，可能为 null
 * @param linuxIcon `linuxIcon` 扩展值，可能为 null
 * @param tomlIcon `[bundle].icon` 解析出的路径，可能为 null
 * @return 实际要喂给 jpackage `--icon` 的路径，没有合适的就返回 null
 */
fun resolveBundleIcon(
    platform: Platform,
    macIcon: Path?,
    windowsIcon: Path?,
    linuxIcon: Path?,
    tomlIcon: Path?,
): Path? {
    val extIcon =
        when (platform) {
            Platform.DARWIN -> macIcon
            Platform.WINDOWS -> windowsIcon
            Platform.LINUX -> linuxIcon
            Platform.UNKNOWN -> return null
        }
    // 扩展图标：配了但文件不存在也跳过，不当报错——图标资源缺失不该阻断整条打包。
    if (extIcon != null && extIcon.toFile().exists()) return extIcon
    // 兜底：toml 的图标按宿主平台后缀匹配；后缀对不上说明它本是为别的平台准备的，跳过而非报错。
    if (tomlIcon != null &&
        tomlIcon.toFile().exists() &&
        tomlIcon.fileName.toString().endsWith(".${platform.iconSuffix()}", ignoreCase = true)
    ) {
        return tomlIcon
    }
    return null
}

// app jar 名，与 jpackage 的 `--main-jar` 对应；jpackage 会把它搬进 app-image 的 `lib/app`。
fun appJarName(settings: BundleSettings): String = "${settings.appName}.jar"

// 独立的前端资源 jar 名：与 app jar 同落 `lib/`，Krema 从 classpath 的 `assets/` 读它。
fun assetsJarName(settings: BundleSettings): String = "${settings.appName}-assets.jar"

// jpackage 产出的 app-image 里那个可执行启动器的落点。
//
// jpackage 已经把启动器按平台惯例摆好了（macOS 在 `Contents/MacOS`，其余平台在 `bin`），
// 所以这里只做「产物目录 + 平台 → 可执行文件」这层换算，不再自己写启动器。
//
// @param target 目标平台标识，形如 `darwin-aarch64`、`windows-x86_64`、`linux-x86_64`
class PlatformBundler(
    private val target: String,
) {
    private val platform = Platform.of(target.substringBefore('-'))

    // app-image 目录：bundle task 与 run task 都得就这个路径达成一致。
    fun appImageDir(settings: BundleSettings): Path = settings.outputDir.resolve(platform.appImageDirName(settings.appName))

    // app-image 里的启动器可执行文件，run task 起的就是它。
    fun launcherPath(settings: BundleSettings): Path {
        val appImage = appImageDir(settings)
        return when (platform) {
            Platform.DARWIN -> appImage.resolve("Contents/MacOS").resolve(settings.appName)
            Platform.WINDOWS -> appImage.resolve("${settings.appName}.exe")
            Platform.LINUX -> appImage.resolve("bin").resolve(settings.appName)
            Platform.UNKNOWN -> throw UnsupportedOperationException("无法为 '$target' 选择启动器")
        }
    }

    // 通过 app-image 的启动器起应用进程。
    //
    // stdin 不继承 Gradle 守护进程那根没人写的管道：被继承后，应用若在 UI 线程读 stdin 会无限阻塞，
    // 表现成「窗口未响应」。改成管道并立刻关掉父进程这端，子进程读到 EOF 而非卡死——
    // 正是「exe 直接双击启动正常、走 run 任务却卡死」的根因。stdout/stderr 仍继承，日志照常可见。
    fun launch(
        settings: BundleSettings,
        env: Map<String, String>,
    ): Process {
        val process =
            ProcessBuilder(launcherPath(settings).toAbsolutePath().toString())
                .apply { environment().putAll(env) }
                .inheritIO()
                .also { it.redirectInput(ProcessBuilder.Redirect.PIPE) }
                .start()
        // 关掉父进程这端，子进程 stdin 立即 EOF：既不阻塞也不残留一个写不进的管道。
        process.outputStream.close()
        return process
    }
}
