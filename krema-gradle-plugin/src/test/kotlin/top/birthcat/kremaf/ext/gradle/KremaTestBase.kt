/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile

/**
 * 插件测试的共用底座：跑构建的 TestKit 接缝、临时工程 fixture 与产物路径推导。
 *
 * 各测试类只留自己那一个 task 的用例；这里只放**两个以上**测试类都要用的东西 ——
 * 放一份只用一次的工具函数不如就地写在用例旁边，读用例时不用跳文件才知道数据是怎么摆的。
 */
abstract class KremaTestBase {
    protected companion object {
        /** `writeToml` 里写的包名，也就是 jpackage `--name` 与 app jar 的名字。 */
        const val APP_NAME = "consumer-app"

        /** `writeMainJava` 写的主类，须与 toml 的 identifier 同包，否则 app jar 的 Main-Class 对不上。 */
        const val MAIN_CLASS = "com.example.app.Main"

        /**
         * 假前端构建产物：只落一个index.html，够断言资产路径用。
         *
         * 落在项目根的 `dist/`，也就是 vite 等前端工具链默认的落点、插件从 `krema.toml` 的 `out_dir`
         * 读到的那个目录。刻意不写成插件当前读的位置：fixture 顺着实现写，插件改错地方时测试照样绿。
         */
        const val FRONTEND_BUILD_SCRIPT = "mkdir -p dist\necho '<html></html>' > dist/index.html"

        /**
         * 建目录的 fixture 写法，Windows 上由 `fakeNpm` 翻成 `if not exist X mkdir X`。
         *
         * 单独拎出来是因为 `out_dir` 那条用例要建的是另一个目录名，Windows 上得跟着翻译。
         */
        fun frontendBuildScript(dir: String = "dist") = "mkdir -p $dir\necho '<html></html>' > $dir/index.html"

        /** jpackage app-image 的目录名，macOS 上多一个 `.app` 后缀。 */
        val APP_IMAGE_DIR_NAME: String = PlatformDetector.detect().appImageDirName(APP_NAME)

        /** 两种分发形态的打包 task。 */
        val BUNDLE_TASKS =
            listOf(
                KremaTaskNames.BUNDLE_DIST,
                KremaTaskNames.BUNDLE_PLATFORM,
            )
    }

    /** 产物根下的某形态 bundle 目录，形如 `build/krema/dist`。 */
    protected fun bundleDir(
        projectDir: File,
        form: String,
    ): Path =
        projectDir
            .toPath()
            .resolve("build/krema")
            .resolve(form)

    protected fun appImageDir(
        projectDir: File,
        form: String,
    ): Path = bundleDir(projectDir, form).resolve(APP_IMAGE_DIR_NAME)

    /** app jar 在 app-image 里的落点，随主 jar 名一起被 jpackage 搬进 `lib/app`。 */
    protected fun appJar(
        projectDir: File,
        form: String,
    ): Path = appImageDir(projectDir, form).resolve("lib/app/$APP_NAME.jar")

    /** 独立的前端资源 jar 在 app-image 里的落点，与 app jar 平铺在 `lib/`。 */
    protected fun assetsJar(
        projectDir: File,
        form: String,
    ): Path = appImageDir(projectDir, form).resolve("lib/$APP_NAME-assets.jar")

    /** 便携包 zip 的路径：`<app>-<version>-<target>.zip`，版本与 target 取自 toml 与宿主平台。 */
    protected fun portableZip(
        projectDir: File,
        form: String,
    ): Path = bundleDir(projectDir, form).resolve("$APP_NAME-0.1.0-${hostTarget()}.zip")

    /** 宿主平台标识，与插件内部拼法同款。 */
    protected fun hostTarget(): String = "${PlatformDetector.detect().name.lowercase()}-${PlatformDetector.getArch()}"

    /**
     * app-image 里那个原生启动器的路径，也就是 run task 真正起的进程。
     *
     * 拿 [PlatformBundler] 算，而不是在测试里重拼一遍平台布局：这份路径是「应用进程长什么样」的唯一真相，
     * 测试另抄一份的话，插件改错布局测试照样绿。路径取绝对形式，因为 `ProcessBuilder` 起的就是绝对路径。
     */
    protected fun launcherPath(
        projectDir: File,
        form: String,
    ): Path =
        PlatformBundler(hostTarget())
            .launcherPath(BundleSettings(appName = APP_NAME, outputDir = bundleDir(projectDir, form)))
            .toAbsolutePath()

    protected fun exeSuffix(): String = if (PlatformDetector.detect() == Platform.WINDOWS) ".exe" else ""

    protected fun jarEntries(jar: Path): List<String> = zipEntries(jar)

    /** 读 jar 的 manifest 主属性，用来核对 `Main-Class` 之类的启动契约。 */
    protected fun jarManifest(jar: Path): Map<String, String> =
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

    protected fun zipEntries(archive: Path): List<String> =
        ZipFile(archive.toFile()).use { zip ->
            zip
                .entries()
                .asSequence()
                .map { it.name }
                .toList()
        }

    protected fun writeToml(projectDir: File) {
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

    protected fun writeSettings(projectDir: File) {
        Files.writeString(
            projectDir.toPath().resolve("settings.gradle.kts"),
            "rootProject.name = \"consumer\"\n",
        )
    }

    /** 一个会一直睡、可被回收的消费者主类，给 run task 一个真实长驻进程。 */
    protected fun writeMainJava(projectDir: File) {
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

    /**
     * 摆一份能跑打包 task 的项目：toml + 主类 + 前端输入，返回假 npm 供 [runGradleWithFakeNpm] 用。
     *
     * 两张打包 task 都经 `kremaBuild` 连到 `kremaFrontendBuild`，故前端命令缺一不可；
     * 前端产物本身不是这些用例的断言点，只为了让流水线走得通。
     */
    protected fun writeBundleProject(projectDir: File): File {
        writeSettings(projectDir)
        writeToml(projectDir)
        writeMainJava(projectDir)
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))
        return fakeNpm
    }

    protected fun runGradle(
        projectDir: File,
        vararg args: String,
        expectFailure: Boolean = false,
    ): BuildResult =
        GradleRunner
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
    protected fun runGradleWithFakeNpm(
        projectDir: File,
        vararg args: String,
        fakeNpm: File? = null,
        expectFailure: Boolean = false,
    ): BuildResult =
        GradleRunner
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

    /**
     * 在项目里写一个可执行假 `npm`，脚本内容由调用方给；返回该可执行文件本身。
     *
     * Windows 上写 `.cmd` 而不是 `#!/bin/sh` 脚本：`CreateProcess` 只认 `.exe` / `.com` / `.bat` / `.cmd`，
     * 交给它一个无扩展名的 sh 脚本会报「不是内部或外部命令」。插件那边按 [commandLineFor] 的约定
     * 在 Windows 上借道 `cmd /c`，所以这里给 `.cmd` 才接得上。
     */
    protected fun fakeNpm(
        projectDir: File,
        script: String,
    ): File {
        val windows = PlatformDetector.detect() == Platform.WINDOWS
        val dir = projectDir.toPath().resolve("fakebin").toFile()
        dir.mkdirs()
        val npm = dir.toPath().resolve(if (windows) "npm.cmd" else "npm").toFile()
        val body = if (windows) windowsScript(script) else script
        Files.writeString(npm.toPath(), body)
        npm.setExecutable(true)
        return npm
    }

    /**
     * 把 POSIX 那两行 fixture 脚本翻成 cmd 能跑的样子。
     *
     * 只认 [frontendBuildScript] 与前端失败那条用例实际用到的那几条 —— `mkdir -p X`、
     * `echo '...' > X`、`echo "..." >&2`、`exit 1`。这是个测试夹具，不是 shell 解释器：
     * 遇到不认识的写法就直接崩出来，而不是悄悄少做一步让断言以另一种原因失败。
     *
     * 单引号必须换成双引号：cmd 不认 POSIX 的单引号，`echo 'x' > f` 会报「此时不应有 <」。
     */
    private fun windowsScript(script: String): String {
        val batch =
            script
                .lineSequence()
                .map { line ->
                    when {
                        line.startsWith("mkdir -p ") ->
                            line.removePrefix("mkdir -p ").let { "if not exist $it mkdir $it" }
                        line.startsWith("echo ") && line.contains(" > ") ->
                            line.replace("'", "\"")
                        line.startsWith("echo ") && line.endsWith(">&2") ->
                            "${line.removeSuffix(">&2")} 1>&2"
                        line == "exit 1" -> "exit /b 1"
                        else -> error("fakeNpm 的 Windows 翻译不认这一行：$line")
                    }
                }.joinToString("\r\n")
        return "@echo off\r\n$batch\r\n"
    }

    /** 写一份 `build.gradle.kts`，前端生产命令直接指向假 npm 的绝对路径（外加 `run build` 占位参数）。 */
    protected fun frontendBuildGradle(npmPath: String) =
        """
        plugins { id("top.birthcat.kremaf.ext") }
        krema {
            frontend {
                buildCommand = "${kotlinString(npmPath)} run build"
            }
        }
        """.trimIndent()

    /**
     * 把路径转义成能塞进 Kotlin 字符串字面量的样子。
     *
     * Windows 的临时目录带 `C:\Users\...`，不转义就插进 `buildCommand = "..."` 里变成
     * `C:\Users` 这种非法转义序列，整个 build.gradle.kts 编译失败 —— 而报错点在构建脚本上，
     * 与被测的插件毫无关系，排查起来白白绕远路。
     */
    protected fun kotlinString(value: String): String = value.replace("\\", "\\\\")

    /** 复刻 GradleRunner 的插件 classpath 注入：把被测插件 jar/classes 加进所有 project 的 buildscript classpath。 */
    protected fun pluginClasspathInitScript(projectDir: File): File {
        val props = Properties().apply { load(File("build/pluginUnderTestMetadata/plugin-under-test-metadata.properties").inputStream()) }
        // 按 File.pathSeparator 切，不写死 ":"：Windows 上分隔符是 ";"，而路径自带盘符 `F:\...`，
        // 按 ":" 切会把每个条目从盘符处劈成两半，classpath 变成一堆不存在的路径 —— 插件解析不到，
        // 报出来的是「Plugin with id ... not found」，看不出是这里切错了。
        val entries = props.getProperty("implementation-classpath").split(File.pathSeparator)
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

    protected fun gradleUserHome(): File {
        val fromEnv = System.getenv("GRADLE_USER_HOME")
        if (!fromEnv.isNullOrBlank()) return File(fromEnv)
        return File(System.getProperty("user.home"), ".gradle")
    }

    /** 定位本机已解压的 Gradle 9.8.0 安装目录，避免 Tooling API 联网下载。 */
    protected fun localGradleInstallation(): File {
        val base = gradleUserHome().resolve("wrapper/dists/gradle-9.8.0-bin")
        val install =
            base.listFiles().orEmpty().firstNotNullOfOrNull { candidate ->
                File(candidate, "gradle-9.8.0").takeIf { it.isDirectory }
            } ?: File("/opt/gradle")
        require(install.isDirectory) { "找不到本地 Gradle 9.8.0 安装：$install" }
        return install
    }

    /** 轮询 OS 进程表，直到出现命令行含 [marker] 的进程（最多约 60s）。 */
    protected fun waitForProc(marker: String): Boolean {
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            if (anyProc(marker)) return true
            Thread.sleep(200)
        }
        return false
    }

    /** 轮询 OS 进程表，直到命令行含 [marker] 的进程消失（最多约 15s）。 */
    protected fun waitGone(marker: String): Boolean {
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

    /** 诊断用：把本项目相关的进程命令行落盘。按项目目录过滤，别把全机器的 gradle/java 都塞进来。 */
    protected fun dumpProcs(
        path: String,
        projectDir: File,
    ) {
        val sb = StringBuilder()
        ProcessHandle.current().descendants().toList().forEach { h ->
            sb.appendLine("DESC: ${h.info().commandLine().orElse("")}")
        }
        ProcessHandle.allProcesses().forEach { h ->
            val cl = h.info().commandLine().orElse("")
            if (cl.contains(projectDir.absolutePath)) sb.appendLine("ALL: $cl")
        }
        File(path).writeText(sb.toString())
    }
}
