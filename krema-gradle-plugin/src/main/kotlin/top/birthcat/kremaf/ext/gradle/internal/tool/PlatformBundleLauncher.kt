/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.tool

import top.birthcat.kremaf.ext.gradle.api.BundleSettings
import top.birthcat.kremaf.ext.gradle.api.Platform
import java.nio.file.Path

/** app-image 启动器：把「app-image 目录 + 平台」换算成可执行文件与进程。 */
internal class PlatformBundleLauncher(
    private val appImageDir: Path,
    private val target: String,
) {
    private val platform = Platform.of(target.substringBefore('-'))

    /** 启动器在 app-image 内的平台落点；平台未知即报错。 */
    @Throws(UnsupportedOperationException::class)
    fun launcherPath(settings: BundleSettings) =
        when (platform) {
            Platform.DARWIN -> appImageDir.resolve("Contents/MacOS").resolve(settings.appName)
            Platform.WINDOWS -> appImageDir.resolve("${settings.appName}.exe")
            Platform.LINUX -> appImageDir.resolve("bin").resolve(settings.appName)
            Platform.UNKNOWN -> throw UnsupportedOperationException("No launcher available for '$target'")
        }

    /**
     * 起应用进程。
     *
     * stdin 立即关闭：继承 daemon 的空管道会让应用读 stdin 时永久阻塞。
     * stdout/stderr 泵回 task 的流：inheritIO 指向 daemon 的 fd，console 看不到输出。
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
        // 关掉父进程这端，子进程 stdin 立即 EOF。
        process.outputStream.close()
        listOf(process.inputStream to System.out, process.errorStream to System.err)
            .forEach { (src, dst) ->
                Thread({ src.copyTo(dst) }, LOG_PUMP_THREAD).apply { isDaemon = true }.start()
            }
        return process
    }

    private companion object {
        private const val LOG_PUMP_THREAD = "krema-app-log"
    }
}
