/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.appImageDirName
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
)

/** 前端资源在 assets jar 里的固定目录名：`prodAssets("/assets")` 按这个约定字面读取，两边必须一致。 */
const val ASSETS_DIR: String = "assets"

/** jar manifest 约定路径：打包时先写一份，避免资源目录同名文件被写第二遍。 */
const val MANIFEST_PATH: String = "META-INF/MANIFEST.MF"

/** krema 产物根下的固定目录名，CI 上传规则照此写死。 */
const val BUNDLE_DIR_NAME: String = "bundle"

/**
 * 与 [BundleFormat.id] 解耦拼写：平台包目录只拼 `platform/<id>` 层级，改 id 即挪路径。
 */
fun platformOutputDirName(formatId: String): String = "platform/$formatId"

/**
 * 四个图标来源：按平台扩展图标优先，[toml] 兜底。四个恒定同行，打包时整组传入，不再散成五个参数。
 *
 * 全部可空、默认 null：没配的维度就是「无」，缺了不该阻断打包。
 */
data class BundleIcons(
    val mac: Path? = null,
    val windows: Path? = null,
    val linux: Path? = null,
    val toml: Path? = null,
)

/** 解析 jpackage `--icon` 路径：扩展图标优先，退到 [BundleIcons.toml]；都没配或不存在则返回 null 且不传 `--icon`（图标缺失不该阻断打包）。 */
fun resolveBundleIcon(
    platform: Platform,
    icons: BundleIcons,
): Path? {
    val extIcon =
        when (platform) {
            Platform.DARWIN -> icons.mac
            Platform.WINDOWS -> icons.windows
            Platform.LINUX -> icons.linux
            Platform.UNKNOWN -> return null
        }
    // 配了但文件不存在也跳过而非报错：图标缺失不该阻断整条打包。
    if (extIcon != null && extIcon.toFile().exists()) return extIcon
    // 兜底：[bundle].icon 按宿主平台后缀匹配；后缀对不上说明它是为别的平台准备的，跳过。
    val toml = icons.toml
    if (toml != null &&
        toml.toFile().exists() &&
        toml.fileName.toString().endsWith(".${platform.iconSuffix()}", ignoreCase = true)
    ) {
        return toml
    }
    return null
}

/** app jar 名，对应 jpackage 的 `--main-jar`。 */
fun appJarName(settings: BundleSettings): String = "${settings.appName}.jar"

/** 独立前端资源 jar 名，与 app jar 同落 `lib/`。 */
fun assetsJarName(settings: BundleSettings): String = "${settings.appName}-assets.jar"

/** app-image 启动器封装：只换算「产物目录 + 平台 → 可执行文件/进程」，启动器由 jpackage 摆放。 */
class PlatformBundler(
    private val target: String,
) {
    private val platform = Platform.of(target.substringBefore('-'))

    /** app-image 目录：bundle 与 run 两个 task 据此达成一致。 */
    fun appImageDir(settings: BundleSettings): Path = settings.outputDir.resolve(platform.appImageDirName(settings.appName))

    /**
     * app-image 里那个跨平台落点的启动器可执行文件，run task 起的就是它。
     *
     * 宿主平台未知时抛 [UnsupportedOperationException]：那台机器根本不该走到启动器解析。
     */
    @Throws(UnsupportedOperationException::class)
    fun launcherPath(settings: BundleSettings): Path {
        val appImage = appImageDir(settings)
        return when (platform) {
            Platform.DARWIN -> appImage.resolve("Contents/MacOS").resolve(settings.appName)
            Platform.WINDOWS -> appImage.resolve("${settings.appName}.exe")
            Platform.LINUX -> appImage.resolve("bin").resolve(settings.appName)
            Platform.UNKNOWN -> throw UnsupportedOperationException("无法为 '$target' 选择启动器")
        }
    }

    /**
     * 通过 app-image 启动器起应用进程。
     *
     * stdin 改为管道并立刻关掉父进程这端：继承 Gradle 守护进程的空管道会让 UI 线程读 stdin 无限阻塞
     * （「双击启动正常、走 run 任务卡死」的根因），关闭后子进程读到 EOF。
     * stdout/stderr 不能 inheritIO：那继承的是 daemon 的 fd，输出掉进 daemon 日志文件而到不了 console，
     * 故泵回 task 自己的流，由 Gradle 按 task 捕获转发。
     */
    fun launch(
        settings: BundleSettings,
        env: Map<String, String>,
    ): Process {
        val process =
            ProcessBuilder(launcherPath(settings).toAbsolutePath().toString())
                .apply { environment().putAll(env) }
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start()
        // 关掉父进程这端，子进程 stdin 立即 EOF：既不阻塞也不残留一个写不进的管道。
        process.outputStream.close()
        listOf(process.inputStream to System.out, process.errorStream to System.err)
            .forEach { (src, dst) ->
                Thread({ src.copyTo(dst) }, "krema-app-log").apply { isDaemon = true }.start()
            }
        return process
    }
}
