/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.testkit.runner.BuildResult
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
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile

class KremaPluginTest {
    private companion object {
        /** 测试 build script 里注册的那张探针 task，用来把插件解析出的资产路径打印出来。 */
        const val ASSETS_PATH_PROBE = "showAssetsPath"

        /** 报告宿主平台与筛后平台包的 task 名。 */
        const val REPORT_TASK = "reportPlatformFormats"

        /** 三平台的格式各写一个，用于验证筛选只留宿主平台的子集。 */
        const val ALL_FORMATS = "BundleFormat.DMG, BundleFormat.MSI, BundleFormat.APP_IMAGE, BundleFormat.DEB"

        /**
         * 假前端构建产物：只落一个 index.html，够断言资产路径用。
         *
         * 落在项目根的 `dist/`，也就是 vite 等前端工具链默认的落点、插件从 `krema.toml` 的 `out_dir` 读到的那个目录。
         * 刻意不写成插件当前读的位置：fixture 顺着实现写，插件改错地方时测试照样绿，那个洞就是这么漏过去的。
         */
        const val FRONTEND_BUILD_SCRIPT = "mkdir -p dist\necho '<html></html>' > dist/index.html"

        /** `writeToml` 里写的包名，也就是 jpackage `--name` 与 app jar 的名字。 */
        const val APP_NAME = "consumer-app"

        /** `writeMainJava` 写的主类，须与 toml 的 identifier 同包，否则 app jar 的 Main-Class 对不上。 */
        const val MAIN_CLASS = "com.example.app.Main"

        /** jpackage app-image 的目录名，macOS 上多一个 `.app` 后缀。 */
        val APP_IMAGE_DIR_NAME = PlatformDetector.detect().appImageDirName(APP_NAME)

        /** 分发形态 × 构建档位的四张打包 task。 */
        val BUNDLE_TASKS =
            listOf(
                KremaTaskNames.BUNDLE_DIST_DEV,
                KremaTaskNames.BUNDLE_DIST_PROD,
                KremaTaskNames.BUNDLE_PLATFORM_DEV,
                KremaTaskNames.BUNDLE_PLATFORM_PROD,
            )
    }

    /** 产物根下的某档 bundle 目录，形如 `build/krema/dist/prod`。 */
    private fun bundleDir(
        projectDir: File,
        vararg path: String,
    ): Path =
        projectDir
            .toPath()
            .resolve("build/krema")
            .resolve(path.first())
            .resolve(path[1])

    private fun appImageDir(
        projectDir: File,
        vararg path: String,
    ): Path = bundleDir(projectDir, *path).resolve(APP_IMAGE_DIR_NAME)

    /** app jar 在 app-image 里的落点，随主 jar 名一起被 jpackage 搬进 `lib/app`。 */
    private fun appJar(
        projectDir: File,
        tier: String,
    ): Path = appImageDir(projectDir, "dist", tier).resolve("lib/app/$APP_NAME.jar")

    /** 便携包 zip 的路径：`<app>-<version>-<target>.zip`，版本与 target 取自 toml 与宿主平台。 */
    private fun portableZip(
        projectDir: File,
        tier: String,
    ): Path {
        val platform = PlatformDetector.detect().name.lowercase()
        val target = "$platform-${PlatformDetector.getArch()}"
        return bundleDir(projectDir, "dist", tier).resolve("$APP_NAME-0.1.0-$target.zip")
    }

    private fun exeSuffix(): String = if (PlatformDetector.detect() == Platform.WINDOWS) ".exe" else ""

    private fun jarEntries(jar: Path): List<String> = zipEntries(jar)

    /** 读 jar 的 manifest 主属性，用来核对 `Main-Class` 之类的启动契约。 */
    private fun jarManifest(jar: Path): Map<String, String> =
        ZipFile(jar.toFile()).use { zip ->
            zip
                .getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))
                .reader()
                .readText()
                .lineSequence()
                .map { it.substringBefore(':').trim() to it.substringAfter(':', "").trim() }
                .filter { (key, value) -> key.isNotEmpty() && value.isNotEmpty() }
                .toMap()
        }

    private fun zipEntries(archive: Path): List<String> =
        ZipFile(archive.toFile()).use { zip ->
            zip
                .entries()
                .asSequence()
                .map { it.name }
                .toList()
        }

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
    @DisplayName("krema 分组列出四张打包 task")
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
        assertEquals(
            BUNDLE_TASKS.size,
            BUNDLE_TASKS.count { result.output.contains(it) },
            "打包 task 应当正好是形态 × 档位的四张：\n${result.output}",
        )
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
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
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
                .resolve("dist/index.html")
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
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val first = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUILD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, first.task(":${KremaTaskNames.BUILD}")?.outcome, first.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("dist/index.html")
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
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        // 打包 task 经 kremaRuntimeImage 连到 jdeps：没有编译产出时 jdeps 先于配置校验失败，
        // 报错就指不到 krema.toml 了，故给一份最小主类让流水线走到配置读取那一步。
        writeMainJava(projectDir)
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

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_DIST_PROD, fakeNpm = fakeNpm, expectFailure = true)

        assertTrue(
            result.output.contains("nope.toml"),
            "显式指定的 krema.toml 不存在应当指到那个路径：\n${result.output}",
        )
    }

    @Test
    @DisplayName("未配置资产路径时默认 web")
    fun assetsPathDefaultsToWeb(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        writeToml(projectDir)
        Files.writeString(projectDir.toPath().resolve("build.gradle"), assetsPathProbeGradle())

        val result = runGradleWithPluginClasspath(projectDir, ASSETS_PATH_PROBE)

        assertEquals(TaskOutcome.SUCCESS, result.task(":$ASSETS_PATH_PROBE")?.outcome, result.output)
        assertTrue(result.output.contains("assetsPath=web"), result.output)
    }

    @Test
    @DisplayName("toml 里显式配置覆盖默认资产路径")
    fun assetsPathFromToml(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("krema.toml"),
            """
            [package]
            name = "consumer-app"

            [build]
            assets_path = "app-ui"
            """.trimIndent(),
        )
        Files.writeString(projectDir.toPath().resolve("build.gradle"), assetsPathProbeGradle())

        val result = runGradleWithPluginClasspath(projectDir, ASSETS_PATH_PROBE)

        assertEquals(TaskOutcome.SUCCESS, result.task(":$ASSETS_PATH_PROBE")?.outcome, result.output)
        assertTrue(result.output.contains("assetsPath=app-ui"), result.output)
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

    /**
     * 摆一份能跑打包 task 的项目：toml + 主类 + 前端输入，返回假 npm 供 [runGradleWithFakeNpm] 用。
     *
     * 四张打包 task 都经 `kremaBuild` 连到 `kremaFrontendBuild`，故前端命令缺一不可；
     * 前端产物本身不是这几条用例的断言点，只为了让流水线走得通。
     */
    private fun writeBundleProject(projectDir: File): File {
        writeSettings(projectDir)
        writeToml(projectDir)
        writeMainJava(projectDir)
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))
        return fakeNpm
    }

    @Test
    @DisplayName("便携包 app-image 自带可执行运行时")
    fun distBundleShipsRuntime(
        @TempDir projectDir: File,
    ) {
        val fakeNpm = writeBundleProject(projectDir)

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_DIST_PROD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DIST_PROD}")?.outcome, result.output)
        val image = appImageDir(projectDir, "dist", "prod")
        val java = image.resolve("lib/runtime/bin/java" + exeSuffix())
        assertTrue(java.toFile().canExecute(), "app-image 里没有可执行的运行时 java：\n${result.output}")
        assertTrue(
            image.resolve("bin").toFile().isDirectory,
            "app-image 里没有启动器目录：\n${result.output}",
        )
        // 启动器按 manifest 里的 Main-Class 找主类；对不上时 jpackage 照样成功，产物却起不来。
        assertEquals(
            MAIN_CLASS,
            jarManifest(appJar(projectDir, "prod"))["Main-Class"],
            "app jar 的 Main-Class 应当与主类产出对得上：\n${result.output}",
        )
    }

    @Test
    @DisplayName("便携包 zip 装的是那份 app-image")
    fun distBundleZipsAppImage(
        @TempDir projectDir: File,
    ) {
        val fakeNpm = writeBundleProject(projectDir)

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_DIST_PROD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DIST_PROD}")?.outcome, result.output)
        val zip = portableZip(projectDir, "prod")
        assertTrue(zip.toFile().isFile, "便携包没落 $zip：\n${result.output}")
        val entries = zipEntries(zip)
        assertTrue(
            entries.any { it.startsWith("$APP_IMAGE_DIR_NAME/lib/runtime/bin/") },
            "zip 里没有 app-image 的运行时：\n$entries",
        )
        assertTrue(
            entries.any { it == "$APP_IMAGE_DIR_NAME/lib/app/$APP_NAME.jar" },
            "zip 里没有 app-image 的 app jar：\n$entries",
        )
        // zip 落在被打的目录里，不排掉产物自身就会把它收进去，解开多出一个空壳同名文件。
        assertTrue(
            entries.none { it.endsWith(".zip") },
            "zip 不该把自己收进去：\n$entries",
        )
    }

    @Test
    @DisplayName("prod 的 app jar 带前端资产")
    fun prodJarHasFrontend(
        @TempDir projectDir: File,
    ) {
        val fakeNpm = writeBundleProject(projectDir)

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_DIST_PROD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DIST_PROD}")?.outcome, result.output)
        val entries = jarEntries(appJar(projectDir, "prod"))
        assertTrue(
            entries.contains("web/index.html"),
            "prod 的 app jar 该带上前端资产：\n$entries",
        )
    }

    @Test
    @DisplayName("前端产物目录认 krema.toml 的 out_dir")
    fun frontendHonorsOutDir(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        writeMainJava(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("krema.toml"),
            """
            [package]
            name = "consumer-app"
            identifier = "com.example.app"
            version = "0.1.0"

            [build]
            out_dir = "custom-dist"
            """.trimIndent(),
        )
        val fakeNpm =
            fakeNpm(
                projectDir,
                "mkdir -p custom-dist\necho '<html></html>' > custom-dist/index.html",
            )
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_DIST_PROD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DIST_PROD}")?.outcome, result.output)
        // 前端命令是用户给的，产物落在哪由 out_dir 说了算；插件不认它就会读空目录，app jar 里没有 web/。
        val entries = jarEntries(appJar(projectDir, "prod"))
        assertTrue(
            entries.contains("web/index.html"),
            "out_dir 改了却没跟着收进 app jar：\n$entries",
        )
    }

    @Test
    @DisplayName("dev 的 app jar 不带前端资产")
    fun devJarHasNoFrontend(
        @TempDir projectDir: File,
    ) {
        val fakeNpm = writeBundleProject(projectDir)

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_DIST_DEV, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DIST_DEV}")?.outcome, result.output)
        val entries = jarEntries(appJar(projectDir, "dev"))
        assertTrue(
            entries.none { it.startsWith("web/") },
            "dev 档走 devUrl 拉前端，app jar 不该带上前端资产：\n$entries",
        )
    }

    @Test
    @DisplayName("四种形态与档位的产物目录分离")
    fun bundleDirsSplitByFormAndTier(
        @TempDir projectDir: File,
    ) {
        val fakeNpm = writeBundleProject(projectDir)

        val result =
            runGradleWithFakeNpm(
                projectDir,
                KremaTaskNames.BUNDLE_DIST_DEV,
                KremaTaskNames.BUNDLE_DIST_PROD,
                KremaTaskNames.BUNDLE_PLATFORM_DEV,
                KremaTaskNames.BUNDLE_PLATFORM_PROD,
                fakeNpm = fakeNpm,
            )

        BUNDLE_TASKS.forEach { name ->
            assertEquals(TaskOutcome.SUCCESS, result.task(":$name")?.outcome, "$name 没成功：\n${result.output}")
        }
        listOf("dist/dev", "dist/prod", "platform/dev", "platform/prod").forEach { path ->
            assertTrue(
                appImageDir(projectDir, *path.split("/").toTypedArray()).toFile().isDirectory,
                "$path 下没有 app-image：\n${result.output}",
            )
        }
    }

    @Test
    @DisplayName("run 中断后不留下孤儿进程")
    fun runDevLeavesNoOrphan(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
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
    @DisplayName("每个平台包格式映射到所属平台与 jpackage type")
    fun formatsMapToPlatformAndType() {
        val expected =
            mapOf(
                "app" to (Platform.DARWIN to "app-image"),
                "dmg" to (Platform.DARWIN to "dmg"),
                "pkg" to (Platform.DARWIN to "pkg"),
                "exe" to (Platform.WINDOWS to "exe"),
                "msi" to (Platform.WINDOWS to "msi"),
                "appimage" to (Platform.LINUX to null),
                "deb" to (Platform.LINUX to "deb"),
                "rpm" to (Platform.LINUX to "rpm"),
            )

        assertEquals(
            expected.keys.sorted(),
            BundleFormat.entries.map { it.id }.sorted(),
            "平台包格式清单与 GLOSSARY 的「平台包」词条不一致",
        )
        BundleFormat.entries.forEach {
            assertEquals(expected.getValue(it.id), it.platform to it.jpackageType, "格式 $it 的平台或 jpackage type 不对")
        }
    }

    @Test
    @DisplayName("bundling 只取宿主平台的平台包子集")
    fun bundlingKeepsOnlyHostFormats(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), bundlingGradle())
        val expected =
            mapOf(
                Platform.DARWIN to listOf("dmg:DARWIN"),
                Platform.WINDOWS to listOf("msi:WINDOWS"),
                Platform.LINUX to listOf("appimage:LINUX", "deb:LINUX"),
            )

        val result = runGradle(projectDir, REPORT_TASK)

        assertEquals(TaskOutcome.SUCCESS, result.task(":$REPORT_TASK")?.outcome, result.output)
        val picked =
            result.output
                .substringAfter("formats=", "")
                .substringBefore('\n')
                .trim()
                .split(",")
                .filter { it.isNotEmpty() }
        assertEquals(
            expected[PlatformDetector.detect()].orEmpty(),
            picked,
            "跨平台写的格式列表应当只留下宿主平台的子集：\n${result.output}",
        )
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
    @DisplayName("platformFormats 为空不出平台包也不报错")
    fun emptyFormatsSkipBundles(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), bundlingGradle(""))

        val result = runGradle(projectDir, REPORT_TASK)

        assertEquals(TaskOutcome.SUCCESS, result.task(":$REPORT_TASK")?.outcome, result.output)
        val picked =
            result.output
                .substringAfter("formats=", "")
                .substringBefore('\n')
                .trim()
        assertEquals("", picked, "默认空集合时不该出任何平台包：\n${result.output}")
    }

    /**
     * 写一份带报告 task 的 `build.gradle.kts`。
     *
     * [formats] 为空串时不写 `bundling { }` 块，用的是默认空集合 —— 空集合下 `.get()` 不炸
     * 正是这条用例要守的。
     */
    private fun bundlingGradle(formats: String = ALL_FORMATS): String {
        val name = REPORT_TASK
        val bundling =
            if (formats.isEmpty()) {
                ""
            } else {
                """
                krema {
                    bundling {
                        platformFormats = setOf($formats)
                    }
                }
                """.trimIndent()
            }
        return """
            import top.birthcat.kremaf.ext.gradle.BundleFormat
            import top.birthcat.kremaf.ext.gradle.PlatformDetector

            plugins { id("top.birthcat.kremaf.ext") }

            $bundling

            tasks.register("$name") {
                val host = PlatformDetector.detect().name
                val picked = krema.bundling.formatsForHost().map { it.id + ":" + it.platform.name }
                doLast {
                    println("host=" + host)
                    println("formats=" + picked.joinToString(","))
                }
            }
            """.trimIndent()
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

    /**
     * 写一份 `build.gradle`，注册一张 task 把插件解析出的资产路径打印出来。
     *
     * 用 Groovy DSL 的 `apply plugin:`：插件 classpath 由 init 脚本注入 buildscript，
     * `plugins { id(...) }` 那条路只认插件仓库，看不到注入的 classpath。
     */
    private fun assetsPathProbeGradle() =
        """
        apply plugin: "top.birthcat.kremaf.ext"

        def loaderClass = Class.forName(
            "top.birthcat.kremaf.ext.gradle.KremaConfigLoader", true, buildscript.classLoader)

        tasks.register("$ASSETS_PATH_PROBE") {
            def toml = layout.projectDirectory.file("krema.toml").asFile.toPath()
            doLast {
                println("assetsPath=" + loaderClass.newInstance().loadOrDefault(toml).build.assetsPath)
            }
        }
        """.trimIndent()

    /**
     * 与 [runGradle] 同款，但用 init 脚本把被测插件的 classpath 注入 buildscript。
     *
     * `withPluginClasspath()` 只让 `plugins { id(...) }` 找得到插件，构建脚本自身拿不到插件的类；
     * 探针要直接调插件的 API，只能走 [pluginClasspathInitScript] 那条注入路径。
     */
    private fun runGradleWithPluginClasspath(
        projectDir: File,
        vararg args: String,
    ): BuildResult {
        val initScript = pluginClasspathInitScript(projectDir)
        return GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments("--configuration-cache", "--stacktrace", "-I", initScript.absolutePath, *args)
            .build()
    }

    private fun writeToml(projectDir: File) {
        Files.writeString(
            projectDir.toPath().resolve("krema.toml"),
            """
            [package]
            name = "consumer-app"
            identifier = "com.example.app"
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
