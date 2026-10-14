/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.PlatformDetector
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * `launch()` 起子进程的三条契约。
 *
 * 用一个假的 app-image 目录代替 jpackage 真产物：启动器只是 app-image 里那个可执行文件，
 * 这里只验「起进程」这一层（管道形态、stdin EOF、环境变量注入），不碰打包产物。
 * stdout 的内容由泵线程消费，测试抢不到，故只断言流的形态而非内容（见 [stdoutIsPiped]）。
 */
class PlatformBundlerLaunchTest {
    /**
     * 造一个假 app-image，把 [script] 写成启动器。
     *
     * [script] 收到产物根目录、返回脚本正文：路径要在 Kotlin 侧嵌进去（Kotlin 模板里再写
     * `\$VAR` 转义容易漏，而 shell 侧展开只能靠环境变量，多绕一层）。
     */
    private fun fakeAppImage(script: (Path) -> String): Pair<Path, BundleSettings> {
        val settings = BundleSettings(appName = APP_NAME, outputDir = Files.createTempDirectory("krema-launch"))
        val bundler = PlatformBundler(currentTarget())
        val launcher = bundler.launcherPath(settings)
        Files.createDirectories(launcher.parent)
        Files.writeString(launcher, "#!/bin/sh\n${script(settings.outputDir)}\n")
        launcher.toFile().setExecutable(true)
        return settings.outputDir to settings
    }

    /**
     * stdout 必须是管道而非继承父进程 fd。
     *
     * 这是 `inheritIO()` 与现实现唯一可观测的差别，且不依赖时序：继承时三个流都是
     * `ProcessBuilder.Null*Stream`（fd 去了父进程），只有管道才换成真流。不断言内容是因为
     * 泵线程正在读 stdout，两边抢会 flaky。
     */
    @Test
    @DisplayName("stdout 走管道而非继承父进程 fd")
    fun stdoutIsPiped() {
        assumeTrue(PlatformDetector.detect() != Platform.WINDOWS, "Windows 无 POSIX shell，跳过")
        val (_, settings) = fakeAppImage({ "exit 0" })

        val process = PlatformBundler(currentTarget()).launch(settings, emptyMap())

        // 继承来的流类型带 Null 前缀；断言「不是」而非「是什么」，具体内部类名随 JDK 变。
        assertFalse(
            process.inputStream.javaClass.simpleName
                .startsWith("Null"),
            "inputStream 是 ${process.inputStream.javaClass.name}，说明 stdout 被 inheritIO 继承了",
        )
        process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /**
     * 子进程读 stdin 必须立刻拿到 EOF。
     *
     * 继承 daemon 的空管道会让 `read` 无限阻塞（「双击正常、走 run 任务卡死」的根因），
     * 故脚本在读到数据时退出码 3、拿到 EOF 时退出码 0 —— 断言 0 即证明 EOF 已送达。
     */
    @Test
    @DisplayName("子进程读 stdin 立刻拿到 EOF")
    fun childSeesStdinEof() {
        assumeTrue(PlatformDetector.detect() != Platform.WINDOWS, "Windows 无 POSIX shell，跳过")
        val (_, settings) = fakeAppImage({ "if read -r _; then exit 3; else exit 0; fi" })

        val process = PlatformBundler(currentTarget()).launch(settings, emptyMap())

        assertEquals(0, exitCodeOf(process), "读到数据说明 stdin 未关闭，子进程会挂在 read 上")
    }

    /** 环境变量必须进到子进程：写进文件来验，避免和泵线程抢 stdout。 */
    @Test
    @DisplayName("环境变量注入到子进程")
    fun envReachesChildProcess() {
        assumeTrue(PlatformDetector.detect() != Platform.WINDOWS, "Windows 无 POSIX shell，跳过")
        var probe: Path? = null
        val (_, settings) =
            fakeAppImage { outDir ->
                // 路径在 Kotlin 侧算出来嵌进脚本：Kotlin 模板里的 `$` 要转义，交给调用点更易错。
                probe = outDir.resolve("probe.txt")
                "printf '%s' \"\$KREMA_PROBE\" > '${probe!!.toAbsolutePath()}'"
            }

        val process = PlatformBundler(currentTarget()).launch(settings, mapOf("KREMA_PROBE" to "probed"))

        assertEquals(0, exitCodeOf(process))
        assertEquals("probed", Files.readString(probe))
    }

    /** 等进程退出并取退出码；超时按失败处理 —— 卡住本身就是契约被破坏的信号。 */
    private fun exitCodeOf(process: Process): Int {
        assertEquals(true, process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS), "子进程 $TIMEOUT_SECONDS 秒未退出")
        return process.exitValue()
    }

    /** `PlatformBundler` 按 `<platform>-<arch>` 取前缀，故用宿主平台拼一个合法的 target。 */
    private fun currentTarget(): String = "${PlatformDetector.detect().name.lowercase()}-x86_64"

    private companion object {
        const val APP_NAME = "krema-fake-app"
        const val TIMEOUT_SECONDS = 10L
    }
}
