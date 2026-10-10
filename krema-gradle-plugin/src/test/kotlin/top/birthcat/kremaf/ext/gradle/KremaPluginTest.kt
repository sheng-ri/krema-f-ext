/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.testkit.runner.GradleRunner
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
import java.io.FileWriter
import java.nio.file.Files
import java.util.Properties

class KremaPluginTest {
    @Test
    @DisplayName("插件应用后注册 krema 扩展")
    fun pluginRegistersExtension(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """
            plugins { id("top.birthcat.kremaf.ext") }

            tasks.register("showExt") {
                val ext = project.extensions.getByName("krema")
                doLast { println("ext=" + ext.javaClass.name) }
            }
            """.trimIndent(),
        )

        val result = runGradle(projectDir, "showExt")

        assertTrue(
            result.output.contains("ext=top.birthcat.kremaf.ext.gradle.KremaExtension"),
            result.output,
        )
    }

    @Test
    @DisplayName("krema 分组声明十张 task")
    fun declaresKremaTasks(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )

        val result = runGradle(projectDir, "tasks", "--group", KremaTaskNames.GROUP)

        KremaTaskNames.ALL.forEach { name ->
            assertTrue(result.output.contains(name), "task $name 未出现在 krema 分组里：\n${result.output}")
        }
    }

    @Test
    @DisplayName("kremaInit 在缺 toml 时生成骨架")
    fun initCreatesSkeletonNoToml(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """
            plugins { id("top.birthcat.kremaf.ext") }

            krema {
                configFile = layout.projectDirectory.file("krema.toml")
                frontend {
                    buildCommand = "npm run build"
                    devCommand = "npm run dev"
                    devUrl = "http://localhost:5173"
                }
            }
            """.trimIndent(),
        )

        val result = runGradle(projectDir, KremaTaskNames.INIT)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.INIT}")?.outcome, result.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("krema.toml")
                .toFile()
                .isFile,
            "kremaInit 没落 krema.toml",
        )
    }

    @Test
    @DisplayName("kremaInit 不写无消费者的键")
    fun initOmitsUnusedKeys(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )

        val result = runGradle(projectDir, KremaTaskNames.INIT)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.INIT}")?.outcome, result.output)
        val toml = Files.readString(projectDir.toPath().resolve("krema.toml"))
        listOf("[window]", "description", "assets_path", "updater", "permissions").forEach { key ->
            assertTrue(!toml.contains(key), "kremaInit 不该写没有消费者的键 $key：\n$toml")
        }
    }

    @Test
    @DisplayName("默认配置缺失不报错")
    fun defaultConfigAbsentOk(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm =
            fakeNpm(
                projectDir,
                "mkdir -p build/krema/dist\nprintf '<html></html>' > build/krema/dist/index.html",
            )
        // 没有 krema.toml 是这条用例的要点；但 kremaBuild 现在也会跑前端，所以给一份最小前端输入。
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUILD, fakeNpm = fakeNpm)

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":${KremaTaskNames.BUILD}")?.outcome,
            "默认路径下没有 krema.toml 不应报错：\n${result.output}",
        )
        assertTrue(
            projectDir
                .toPath()
                .resolve("build/krema/dist/index.html")
                .toFile()
                .isFile,
            "前端没产出 dist：\n${result.output}",
        )
    }

    @Test
    @DisplayName("二次构建前端 dist 命中 UP-TO-DATE")
    fun frontendDistUpToDate(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm =
            fakeNpm(
                projectDir,
                "mkdir -p build/krema/dist\nprintf '<html></html>' > build/krema/dist/index.html",
            )
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val first = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUILD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, first.task(":${KremaTaskNames.BUILD}")?.outcome, first.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("build/krema/dist/index.html")
                .toFile()
                .isFile,
            "首次运行没产出 dist：\n${first.output}",
        )

        val second = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUILD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":${KremaTaskNames.BUILD}")?.outcome, second.output)
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":${KremaTaskNames.FRONTEND_BUILD}")?.outcome,
            "前端 task 输入没变时应当 UP-TO-DATE：\n${second.output}",
        )
    }

    @Test
    @DisplayName("前端构建失败透出命令输出")
    fun frontendFailureShowsOutput(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val marker = "FAKE_NPM_DIAGNOSTIC_LINE_abc123"
        val fakeNpm = fakeNpm(projectDir, "echo \"$marker\" >&2\nexit 1")
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val result =
            runGradleWithFakeNpm(
                projectDir,
                KremaTaskNames.BUILD,
                fakeNpm = fakeNpm,
                expectFailure = true,
            )

        assertTrue(
            result.output.contains("前端构建失败"),
            "失败信息应当带上命令输出：\n${result.output}",
        )
        assertTrue(
            result.output.contains(marker),
            "失败信息应当包含命令自身打印的内容：\n${result.output}",
        )
    }

    @Test
    @DisplayName("显式指定缺失配置硬失败")
    fun explicitConfigFileMissingFails(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, "mkdir -p build/krema/dist\necho hi > build/krema/dist/index.html")
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """
            plugins { id("top.birthcat.kremaf.ext") }
            krema {
                configFile = layout.projectDirectory.file("nope.toml")
                frontend { buildCommand = "${fakeNpm.absolutePath} run build" }
            }
            """.trimIndent(),
        )

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_PROD, fakeNpm = fakeNpm, expectFailure = true)

        assertTrue(
            result.output.contains("nope.toml"),
            "显式指定的 krema.toml 不存在应当指到那个路径：\n${result.output}",
        )
    }

    @Test
    @DisplayName("kremaSignerGenerate 生成 Ed25519 密钥对")
    fun signerGenerateKeyPair(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )
        writeToml(projectDir)

        val result = runGradle(projectDir, KremaTaskNames.SIGNER_GENERATE)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.SIGNER_GENERATE}")?.outcome, result.output)
        val key = projectDir.toPath().resolve("build/krema/keys/krema.key")
        assertTrue(key.toFile().isFile, "kremaSignerGenerate 没落私钥")
        assertTrue(
            Files.readString(key).startsWith("-----BEGIN PRIVATE KEY-----"),
            "私钥不是 PEM 形状：\n${Files.readString(key)}",
        )
    }

    @Test
    @DisplayName("kremaBundleProd 落到 prod 输出目录")
    fun bundleProdOutputDir(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, "mkdir -p build/krema/dist\necho hi > build/krema/dist/index.html")
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))
        writeToml(projectDir)

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_PROD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_PROD}")?.outcome, result.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("build/krema/prod")
                .toFile()
                .exists(),
            "kremaBundleProd 没落 prod 产物目录",
        )
    }

    @Test
    @DisplayName("dev 与 prod 档产物目录分离")
    fun bundleDevProdSeparateDirs(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, "mkdir -p build/krema/dist\necho hi > build/krema/dist/index.html")
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))
        writeToml(projectDir)

        val result =
            runGradleWithFakeNpm(
                projectDir,
                KremaTaskNames.BUNDLE_DEV,
                KremaTaskNames.BUNDLE_PROD,
                fakeNpm = fakeNpm,
            )

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DEV}")?.outcome, result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_PROD}")?.outcome, result.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("build/krema/dev")
                .toFile()
                .exists(),
            "dev 档没落 build/krema/dev",
        )
        assertTrue(
            projectDir
                .toPath()
                .resolve("build/krema/prod")
                .toFile()
                .exists(),
            "prod 档没落 build/krema/prod",
        )
    }

    @Test
    @DisplayName("kremaBundleProd 二次执行命中 UP-TO-DATE")
    fun bundleProdUpToDate(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, "mkdir -p build/krema/dist\necho hi > build/krema/dist/index.html")
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))
        writeToml(projectDir)

        val first = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_PROD, fakeNpm = fakeNpm)
        assertEquals(TaskOutcome.SUCCESS, first.task(":${KremaTaskNames.BUNDLE_PROD}")?.outcome, first.output)

        val second = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_PROD, fakeNpm = fakeNpm)
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":${KremaTaskNames.BUNDLE_PROD}")?.outcome,
            "二次执行 kremaBundleProd 应当 UP-TO-DATE：\n${second.output}",
        )
    }

    @Test
    @DisplayName("run 中断后不留下孤儿进程")
    fun runDevLeavesNoOrphan(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, "mkdir -p build/krema/dist\necho hi > build/krema/dist/index.html")
        val fakeDev = fakeDevServer(projectDir)
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(
            projectDir.toPath().resolve("build.gradle"),
            runBuildGradle(fakeNpm.absolutePath, fakeDev.absolutePath),
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
                .forTasks(KremaTaskNames.BUNDLE_RUN_DEV)
                .withArguments("--offline", "--configuration-cache", "-I", initScript.absolutePath)
                .withCancellationToken(tokenSource.token())
        launcher.setStandardOutput(FileOutputStream("/tmp/krema-run.log"))
        launcher.setStandardError(FileOutputStream("/tmp/krema-run.log"))
        var buildFailure: Throwable? = null
        val buildThread = Thread { runCatching { launcher.run() }.onFailure { buildFailure = it } }
        buildThread.start()

        val appUp = waitForProc("com.example.app.Main")
        val devUp = waitForProc("devserver")
        dumpProcs("/tmp/krema-procs.log")
        if (!appUp) error("app 进程没起来；buildFailure=${buildFailure?.stackTraceToString()}\n见 /tmp/krema-run.log / /tmp/krema-procs.log")
        assertTrue(devUp, "dev server 进程没起来，无法验证回收")
        // 等价于 Ctrl-C：取消本次构建，守护进程据此打断 run task 线程、触发 cleanup 回收进程树。
        tokenSource.cancel()
        buildThread.join(60_000)

        assertTrue(waitGone("com.example.app.Main"), "run 中断后 app 进程仍存活")
        assertTrue(waitGone("devserver"), "run 中断后 dev server 进程仍存活")
        connection.close()
    }

    @Test
    @DisplayName("运行时镜像里有可执行 java 且小于全量 JDK")
    fun runtimeImageSlimmerThanJdk(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )
        writeToml(projectDir)
        writeMainJava(projectDir)

        val result = runGradle(projectDir, KremaTaskNames.RUNTIME_IMAGE)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.RUNTIME_IMAGE}")?.outcome, result.output)
        val image = projectDir.toPath().resolve("build/krema/runtime")
        assertTrue(
            image.resolve("bin/java").toFile().canExecute(),
            "运行时镜像里没有可执行的 bin/java：\n${result.output}",
        )
        assertTrue(
            treeSize(image.toFile()) < treeSize(File(System.getProperty("java.home"))),
            "运行时镜像应比全量 JDK 小，否则说明 jlink 没生效：\n${result.output}",
        )
    }

    @Test
    @DisplayName("运行时镜像二次执行命中 UP-TO-DATE")
    fun runtimeImageUpToDate(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )
        writeToml(projectDir)
        writeMainJava(projectDir)

        val first = runGradle(projectDir, KremaTaskNames.RUNTIME_IMAGE)

        assertEquals(TaskOutcome.SUCCESS, first.task(":${KremaTaskNames.RUNTIME_IMAGE}")?.outcome, first.output)

        val second = runGradle(projectDir, KremaTaskNames.RUNTIME_IMAGE)

        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":${KremaTaskNames.RUNTIME_IMAGE}")?.outcome,
            "classpath 输入没变时运行时镜像应当 UP-TO-DATE：\n${second.output}",
        )
    }

    /** 目录内所有普通文件的字节数之和，用来比运行时镜像与全量 JDK 的体积。 */
    private fun treeSize(root: File): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** 复刻 GradleRunner 的插件 classpath 注入：把被测插件 jar/classes 加进所有 project 的 buildscript classpath。 */
    private fun pluginClasspathInitScript(projectDir: File): File {
        val props = Properties().apply { load(File("build/pluginUnderTestMetadata/plugin-under-test-metadata.properties").inputStream()) }
        val entries = props.getProperty("implementation-classpath").split(":")
        val filesArg = entries.joinToString("', '", "'", "'")
        val script =
            """
            initscript {
                dependencies { classpath files($filesArg) }
            }
            allprojects {
                buildscript {
                    dependencies { classpath files($filesArg) }
                }
            }
            """.trimIndent()
        return projectDir.resolve("plugin-classpath.init.gradle").also { it.writeText(script) }
    }

    private fun gradleUserHome(): File {
        val fromEnv = System.getenv("GRADLE_USER_HOME")
        if (!fromEnv.isNullOrBlank()) return File(fromEnv)
        return File(System.getProperty("user.home"), ".gradle")
    }

    /** 定位本机已解压的 Gradle 9.8.0 安装目录，避免 Tooling API 联网下载。 */
    private fun localGradleInstallation(): File {
        val base = gradleUserHome().resolve("wrapper/dists/gradle-9.8.0-bin")
        val install =
            base.listFiles().orEmpty().firstNotNullOfOrNull { candidate ->
                File(candidate, "gradle-9.8.0").takeIf { it.isDirectory }
            } ?: File("/opt/gradle")
        require(install.isDirectory) { "找不到本地 Gradle 9.8.0 安装：$install" }
        return install
    }

    private fun writeToml(projectDir: File) {
        Files.writeString(
            projectDir.toPath().resolve("krema.toml"),
            """
            [package]
            name = "consumer-app"
            identifier = "com.example.consumer"
            version = "0.1.0"
            """.trimIndent(),
        )
    }

    private fun writeSettings(projectDir: File) {
        Files.writeString(
            projectDir.toPath().resolve("settings.gradle.kts"),
            "rootProject.name = \"consumer\"\n",
        )
    }

    private fun runGradle(
        projectDir: File,
        vararg args: String,
        expectFailure: Boolean = false,
    ) = GradleRunner
        .create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*args, "--configuration-cache", "--stacktrace")
        .let { if (expectFailure) it.buildAndFail() else it.build() }

    /**
     * 与 [runGradle] 同款，但可选地用一个假 `npm` 覆盖 PATH。
     *
     * 唯一接缝就是 TestKit：生产代码不为可测性留任何执行口，所以测试自己写一个假可执行文件，
     * 前置到 PATH 让插件的 `ExecOperations` 找到它（[withEnvironment] 会整体替换环境，
     * 故只保留 PATH，并把原 PATH 接在后面）。长驻 daemon 会缓存进程环境，调用方同时把
     * `buildCommand` 指向假 npm 的绝对路径，双保险。不传 [fakeNpm] 就不改环境。
     */
    private fun runGradleWithFakeNpm(
        projectDir: File,
        vararg args: String,
        fakeNpm: File? = null,
        expectFailure: Boolean = false,
    ) = GradleRunner
        .create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments("--configuration-cache", "--stacktrace", *args)
        .let { runner ->
            val configured =
                if (fakeNpm != null) {
                    runner.withEnvironment(
                        mapOf("PATH" to "${fakeNpm.parentFile.absolutePath}${File.pathSeparator}${System.getenv("PATH")}"),
                    )
                } else {
                    runner
                }
            if (expectFailure) configured.buildAndFail() else configured.build()
        }

    /** 在项目里写一个可执行假 `npm`，脚本内容由调用方给；返回该可执行文件本身。 */
    private fun fakeNpm(
        projectDir: File,
        script: String,
    ): File {
        val dir = projectDir.toPath().resolve("fakebin").toFile()
        dir.mkdirs()
        val npm = dir.toPath().resolve("npm").toFile()
        Files.writeString(npm.toPath(), "#!/bin/sh\n$script\n")
        npm.setExecutable(true)
        return npm
    }

    /** 写一份 `build.gradle.kts`，前端生产命令直接指向假 npm 的绝对路径（外加 `run build` 占位参数）。 */
    private fun frontendBuildGradle(npmPath: String) =
        """
        plugins { id("top.birthcat.kremaf.ext") }
        krema {
            frontend {
                buildCommand = "$npmPath run build"
            }
        }
        """.trimIndent()

    /**
     * 写一份 `build.gradle.kts`，前端生产与 dev server 都指向假可执行文件的绝对路径。
     *
     * 用 `apply(plugin = ...)` 而非 `plugins { id }`：本测试绕开 GradleRunner、直接用 Tooling API 起构建，
     * 插件 classpath 靠 init 脚本注入到 buildscript classpath，`apply` 能据此解析插件 id，而 `plugins {}` 只认插件仓库。
     */
    private fun runBuildGradle(
        npmPath: String,
        devPath: String,
    ) = """
        apply plugin: "top.birthcat.kremaf.ext"
        krema {
            mainClass = "com.example.app.Main"
            frontend {
                buildCommand = "$npmPath run build"
                devCommand = "$devPath"
            }
        }
        """.trimIndent()

    /** 一个一直 sleep 的假 dev server，命令里带可识别的 `devserver` 字样。 */
    private fun fakeDevServer(projectDir: File): File {
        val dir = projectDir.toPath().resolve("fakebin").toFile()
        dir.mkdirs()
        val dev = dir.toPath().resolve("devserver").toFile()
        Files.writeString(dev.toPath(), "#!/bin/sh\nsleep 3600\n")
        dev.setExecutable(true)
        return dev
    }

    /** 一个会一直睡、可被回收的消费者主类，给 run task 一个真实长驻进程。 */
    private fun writeMainJava(projectDir: File) {
        val src = projectDir.toPath().resolve("src/main/java/com/example/app").toFile()
        src.mkdirs()
        Files.writeString(
            src.toPath().resolve("Main.java"),
            """
            package com.example.app;
            public class Main {
                public static void main(String[] args) throws InterruptedException {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(500);
                    }
                }
            }
            """.trimIndent(),
        )
    }

    /** 轮询 OS 进程表，直到出现命令行含 [marker] 的进程（最多约 60s）。 */
    private fun waitForProc(marker: String): Boolean {
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            if (anyProc(marker)) return true
            Thread.sleep(200)
        }
        return false
    }

    /** 轮询 OS 进程表，直到命令行含 [marker] 的进程消失（最多约 15s）。 */
    private fun waitGone(marker: String): Boolean {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (!anyProc(marker)) return true
            Thread.sleep(200)
        }
        return !anyProc(marker)
    }

    private fun anyProc(marker: String): Boolean =
        ProcessHandle.allProcesses().anyMatch { handle ->
            handle
                .info()
                .commandLine()
                .orElse("")
                .contains(marker)
        }

    private fun dumpProcs(path: String) {
        val sb = StringBuilder()
        ProcessHandle.current().descendants().toList().forEach { h ->
            sb.appendLine("DESC: ${h.info().commandLine().orElse("")}")
        }
        ProcessHandle.allProcesses().forEach { h ->
            val cl = h.info().commandLine().orElse("")
            if (cl.contains("devserver") || cl.contains("com.example.app") || cl.contains("gradle")) {
                sb.appendLine("ALL: $cl")
            }
        }
        File(path).writeText(sb.toString())
    }
}
