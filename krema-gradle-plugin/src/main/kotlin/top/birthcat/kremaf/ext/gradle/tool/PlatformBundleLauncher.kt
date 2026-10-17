/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.tool

import top.birthcat.kremaf.ext.gradle.domain.BundleSettings
import top.birthcat.kremaf.ext.gradle.domain.Platform
import java.nio.file.Path

/** app-image 启动器封装：只换算「产物目录 + 平台 → 可执行文件/进程」，启动器由 jpackage 摆放。 */
class PlatformBundleLauncher(
    private val appImageDir: Path,
    private val target: String,
) {
    private val platform = Platform.of(target.substringBefore('-'))

    /**
     * app-image 里那个跨平台落点的启动器可执行文件，run task 起的就是它。
     *
     * 宿主平台未知时抛 [UnsupportedOperationException]：那台机器根本不该走到启动器解析。
     */
    @Throws(UnsupportedOperationException::class)
    fun launcherPath(settings: BundleSettings) =
        when (platform) {
            Platform.DARWIN -> appImageDir.resolve("Contents/MacOS").resolve(settings.appName)
            Platform.WINDOWS -> appImageDir.resolve("${settings.appName}.exe")
            Platform.LINUX -> appImageDir.resolve("bin").resolve(settings.appName)
            Platform.UNKNOWN -> throw UnsupportedOperationException("No launcher available for '$target'")
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
