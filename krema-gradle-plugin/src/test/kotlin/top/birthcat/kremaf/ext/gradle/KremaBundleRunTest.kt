/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/** run task：起并中断应用进程，验证回收不留孤儿。 */
class KremaBundleRunTest : KremaTestBase() {
    @Test
    @DisplayName("run 中断后不留下孤儿进程")
    fun runLeavesNoOrphan(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(
            projectDir.toPath().resolve("build.gradle"),
            runBuildGradle(fakeNpm.absolutePath),
        )
        writeToml(projectDir)
        writeMainJava(projectDir)

        // TestKit 9.8 不接受 --no-daemon，构建必跑在守护进程里，而守护进程不认 OS 信号的优雅取消，
        // 故只能用 Tooling API 的 CancellationToken 取消——这正是对用户 Ctrl-C 的等价模拟。
        // 插件 classpath 注入用一份复刻 GradleRunner 的 init 脚本完成。
        val initScript = pluginClasspathInitScript(projectDir)
        val connector =
            GradleConnector
                .newConnector()
                .forProjectDirectory(projectDir)
                .useInstallation(localGradleInstallation())
                .useGradleUserHomeDir(gradleUserHome())
        val connection = connector.connect()
        val tokenSource = GradleConnector.newCancellationTokenSource()
        val launcher =
            connection
                .newBuild()
                .forTasks(KremaTaskNames.BUNDLE_RUN)
                .withArguments("--configuration-cache", "-I", initScript.absolutePath)
                .withCancellationToken(tokenSource.token())
        launcher.setStandardOutput(FileOutputStream("/tmp/krema-run.log"))
        launcher.setStandardError(FileOutputStream("/tmp/krema-run.log"))
        var buildFailure: Throwable? = null
        val buildThread = Thread { runCatching { launcher.run() }.onFailure { buildFailure = it } }
        buildThread.start()

        // 盯启动器自己的路径，而不是主类名：jpackage 产出的原生启动器把 JVM 跑在自己进程里，
        // 命令行上只有启动器路径，`com.example.app.Main` 一个字都不会出现。
        // 拿主类名当marker 反而会撞上打包期的 jpackage 进程（它带 `--main-class` 参数），
        // 于是「等进程出现」等到了 jpackage、「等进程消失」等到了 jpackage 退出，
        // 断言全程为真却与 run task 起的应用进程无关——回收真坏掉也照样绿。
        val appMarker = launcherPath(projectDir, "platform").toString()
        val appUp = waitForProc(appMarker)
        dumpProcs("/tmp/krema-procs.log", projectDir)
        if (!appUp) {
            error(
                "app 进程没起来；buildFailure=${buildFailure?.stackTraceToString()}\n" +
                    "见 /tmp/krema-run.log / /tmp/krema-procs.log",
            )
        }
        // 等价于 Ctrl-C：取消本次构建，守护进程据此打断 run task 线程、触发 cleanup 回收进程树。
        tokenSource.cancel()
        buildThread.join(60_000)

        assertTrue(waitGone(appMarker), "run 中断后 app 进程仍存活")
        connection.close()
    }

    /**
     * 写一份 `build.gradle`，前端生产命令指向假可执行文件的绝对路径。
     *
     * 用 `apply(plugin = ...)` 而非 `plugins { id }`：本用例绕开 GradleRunner、直接用 Tooling API 起构建，
     * 插件 classpath 靠 init 脚本注入到 buildscript classpath，`apply` 能据此解析插件 id，
     * 而 `plugins {}` 只认插件仓库。
     */
    private fun runBuildGradle(npmPath: String) =
        """
        apply plugin: "top.birthcat.kremaf.ext"
        krema {
            mainClass = "com.example.app.Main"
            frontend {
                buildCommand = "${kotlinString(npmPath)} run build"
            }
        }
        """.trimIndent()
}
