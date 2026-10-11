/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

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
