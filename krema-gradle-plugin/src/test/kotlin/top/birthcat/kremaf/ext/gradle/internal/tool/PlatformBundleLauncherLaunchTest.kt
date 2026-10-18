/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.internal.tool

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import top.birthcat.kremaf.ext.gradle.api.BundleSettings
import top.birthcat.kremaf.ext.gradle.api.Platform
import top.birthcat.kremaf.ext.gradle.internal.domain.PlatformDetector
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * `launch()` 起子进程的三条契约。
 *
 * 用假 app-image 代替 jpackage 产物，只验进程层：
 * 管道形态、stdin EOF、环境变量注入。
 */
class PlatformBundleLauncherLaunchTest {
    /** 造假 app-image，把 [script] 放到启动器位置。 */
    private fun fakeAppImage(script: (Path) -> String): Pair<Path, BundleSettings> {
        val appImageDir = Files.createTempDirectory("krema-launch")
        val settings = settings()
        val launcher = PlatformBundleLauncher(appImageDir, currentTarget()).launcherPath(settings)
        Files.createDirectories(launcher.parent)
        Files.writeString(launcher, "#!/bin/sh\n${script(appImageDir)}\n")
        launcher.toFile().setExecutable(true)
        return appImageDir to settings
    }

    /**
     * stdout 必须是管道而非继承父进程 fd。
     *
     * 继承时三个流都是 `ProcessBuilder.Null*Stream`，只有管道才是真流；
     * 不断言内容，泵线程正在读 stdout，两边抢会 flaky。
     */
    @Test
    @DisplayName("stdout 走管道而非继承父进程 fd")
    fun stdoutIsPiped() {
        assumePosixShell()
        val (appImageDir, settings) = fakeAppImage { "exit 0" }

        val process = PlatformBundleLauncher(appImageDir, currentTarget()).launch(settings, emptyMap())

        assertFalse(
            process.inputStream.javaClass.simpleName
                .startsWith("Null"),
            "inputStream is ${process.inputStream.javaClass.name}, stdout was inherited",
        )
        assertTrue(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    /**
     * 子进程读 stdin 必须立刻拿到 EOF。
     *
     * 继承 daemon 的空管道会让 `read` 无限阻塞；
     * 脚本读到数据时退出 3，EOF 时退出 0。
     */
    @Test
    @DisplayName("子进程读 stdin 立刻拿到 EOF")
    fun childSeesStdinEof() {
        assumePosixShell()
        val (appImageDir, settings) = fakeAppImage { "if read -r _; then exit 3; else exit 0; fi" }

        val process = PlatformBundleLauncher(appImageDir, currentTarget()).launch(settings, emptyMap())

        assertEquals(0, exitCodeOf(process), "stdin was not closed, the child hangs on read")
    }

    /** 环境变量必须进到子进程：写文件验证，避免和泵线程抢 stdout。 */
    @Test
    @DisplayName("环境变量注入到子进程")
    fun envReachesChildProcess() {
        assumePosixShell()
        val probe = Files.createTempFile("krema-probe", ".txt")
        val (appImageDir, settings) =
            fakeAppImage {
                "printf '%s' \"\$KREMA_PROBE\" > '$probe'"
            }

        val process = PlatformBundleLauncher(appImageDir, currentTarget()).launch(settings, mapOf("KREMA_PROBE" to "probed"))

        assertEquals(0, exitCodeOf(process))
        assertEquals("probed", Files.readString(probe))
    }

    /** 等进程退出并取退出码；卡住本身就是契约被破坏。 */
    private fun exitCodeOf(process: Process): Int {
        assertTrue(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Child did not exit within $TIMEOUT_SECONDS s")
        return process.exitValue()
    }

    /** 用宿主平台拼一个 platform-arch 目标；Windows 没有 POSIX shell。 */
    private fun assumePosixShell() = assumeTrue(PlatformDetector.detect() != Platform.WINDOWS, "Windows has no POSIX shell")

    private fun currentTarget() = "${PlatformDetector.detect().name.lowercase()}-${PlatformDetector.getArch()}"

    private fun settings() =
        BundleSettings(
            appName = APP_NAME,
            identifier = "com.example.kremafake",
            version = "0.0.0",
            mainClass = "com.example.kremafake.Main",
        )

    private companion object {
        const val APP_NAME = "krema-fake-app"
        const val TIMEOUT_SECONDS = 10L
    }
}
