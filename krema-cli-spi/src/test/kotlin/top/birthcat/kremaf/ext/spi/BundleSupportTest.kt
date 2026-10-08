/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import top.birthcat.kremaf.ext.spi.iml.delegate.BundleSupportFactoryDelegate
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioFileSystem
import top.birthcat.kremaf.ext.spi.port.BundleSettings
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.CommandRunner
import java.nio.file.Files
import java.nio.file.Path

/**
 * 平台 bundler 的命令形状验证。
 *
 * 假 [CommandRunner] 只记命令、不真起进程 —— Windows `.exe` 与 Linux `.deb` 的断言
 * 因此能在同一个 CI 容器里跑，不必真有一台 Windows 机器。
 */
class BundleSupportTest {
    private val fs = NioFileSystem()

    @Test
    fun windowsBundlesExeThroughJpackage(
        @TempDir root: Path,
    ) {
        val runner = RecordingCommandRunner(root)
        val settings = stagedSettings(root, "krema-app")
        val dest = defaultOutput(root).resolve("windows")

        val result =
            BundleSupportFactoryDelegate(fs, runner)
                .create("windows-x86_64")
                .createProductionBundle(settings)

        assertEquals(dest, result)
        val command = runner.lastCommand()
        assertEquals("jpackage", Path.of(command.first()).fileName.toString())
        assertOption(command, "--type", "exe")
        assertOption(command, "--main-jar", "krema-app.jar")
        assertOption(command, "--main-class", settings.mainClass)
        assertOption(command, "--name", "krema-app")
        assertOption(command, "--app-version", settings.version)
        assertTrue(command.contains("--win-dir-chooser"), command.toString())
        // `--input` 必须指向摊平目录，jar 与原生库都在里头。
        val input = Path.of(optionValue(command, "--input"))
        assertTrue(Files.isRegularFile(input.resolve("krema-app.jar")), "input 目录里没有主 jar")
        assertTrue(Files.isRegularFile(input.resolve("libkrema_webview.so")), "input 目录里没有原生库")
    }

    @Test
    fun linuxBundlesDebThroughJpackage(
        @TempDir root: Path,
    ) {
        val runner = RecordingCommandRunner(root)
        val settings = stagedSettings(root, "krema-app")
        val dest = defaultOutput(root).resolve("linux")

        val result =
            BundleSupportFactoryDelegate(fs, runner)
                .create("linux-x86_64")
                .createProductionBundle(settings)

        assertEquals(dest, result)
        val command = runner.lastCommand()
        assertEquals("jpackage", Path.of(command.first()).fileName.toString())
        assertOption(command, "--type", "deb")
        assertOption(command, "--main-jar", "krema-app.jar")
        assertOption(command, "--main-class", settings.mainClass)
        assertTrue(command.contains("--linux-shortcut"), command.toString())
    }

    @Test
    fun buildsMainJarWhenClasspathLacksIt(
        @TempDir root: Path,
    ) {
        val runner = RecordingCommandRunner(root)
        // classpath 只有第三方 jar，应用自己的输出是个 classes 目录：
        // 应用输出不属于自己的 runtimeClasspath，所以 bundler 得自己压主 jar。
        val libJar = root.resolve("libs").also { fs.writeText(it.resolve("guava.jar"), "jar") }
        val classesDir = root.resolve("classes").also { fs.writeText(it.resolve("Main.class"), "x") }
        val settings =
            BundleSettings(
                appName = "krema-app",
                outputDir = root.resolve("out"),
                version = "1.2.3",
                mainClass = "com.example.Main",
                classpath = Classpath(listOf(libJar.resolve("guava.jar")), classesDir),
            )

        BundleSupportFactoryDelegate(fs, runner)
            .create("linux-x86_64")
            .createProductionBundle(settings)

        val jarCall = runner.allCommands().first { Path.of(it.first()).fileName.toString() == "jar" }
        assertTrue(jarCall.contains("--main-class"), jarCall.toString())
        assertOption(jarCall, "--main-class", "com.example.Main")
        assertTrue(optionValue(jarCall, "--file").endsWith("krema-app.jar"), jarCall.toString())
        assertOption(runner.lastCommand(), "--main-jar", "krema-app.jar")
    }

    @Test
    fun onlyMacOSHandRollsClasspath(
        @TempDir root: Path,
    ) {
        val factory = BundleSupportFactoryDelegate(fs, RecordingCommandRunner(root))

        assertTrue(factory.create("darwin-aarch64").deploysClasspath, "macOS 手搓 .app，要自己摊 classpath")
        assertFalse(factory.create("windows-x86_64").deploysClasspath, "jpackage 自己装 runtime，不用再摊")
        assertFalse(factory.create("linux-x86_64").deploysClasspath, "jpackage 自己装 runtime，不用再摊")
    }

    @Test
    fun macOsKeepsHandRolledAppBundle(
        @TempDir root: Path,
    ) {
        val bundler = BundleSupportFactoryDelegate(fs, RecordingCommandRunner(root)).create("darwin-aarch64")
        val settings = stagedSettings(root, "krema-app")

        val bundleDir = bundler.createProductionBundle(settings)

        assertTrue(bundler.requiresBundleForNativeFeatures)
        assertTrue(Files.isRegularFile(bundleDir.resolve("Contents/Info.plist")), "macOS 没落 Info.plist")
        assertTrue(Files.isRegularFile(bundleDir.resolve("Contents/MacOS/krema-app")), "macOS 没落启动器")
    }

    /** 造一份带 classpath 的打包输入，classpath 里有一个 jar 与一个原生库。 */
    private fun stagedSettings(
        root: Path,
        appName: String,
    ): BundleSettings {
        val jarsDir = root.resolve("jars").also { fs.writeText(it.resolve("$appName.jar"), "jar") }
        val nativeDir = root.resolve("native").also { fs.writeText(it.resolve("libkrema_webview.so"), "so") }
        return BundleSettings(
            appName = appName,
            outputDir = defaultOutput(root),
            version = "1.2.3",
            mainClass = "com.example.Main",
            classpath = Classpath(listOf(jarsDir.resolve("$appName.jar")), root.resolve("classes"), nativeDir),
        )
    }

    private fun defaultOutput(root: Path): Path = root.resolve("out")

    private fun assertOption(
        command: List<String>,
        name: String,
        value: String,
    ) {
        assertEquals(value, optionValue(command, name), "命令里 $name 不对：$command")
    }

    private fun optionValue(
        command: List<String>,
        name: String,
    ): String {
        val index = command.indexOf(name)
        assertTrue(index >= 0 && index + 1 < command.size, "命令里缺 $name：$command")
        return command[index + 1]
    }

    /** 记命令的假 runner：`which` 假装机器上有 `jpackage`。 */
    private class RecordingCommandRunner(
        root: Path,
    ) : CommandRunner {
        private val fakeBin = root.resolve("fakebin")
        private val commands = mutableListOf<List<String>>()

        init {
            // `which("jpackage")` 的返回值取 .parent 当 bin 目录，所以假 jpackage 得真落盘。
            Files.createDirectories(fakeBin)
            Files.writeString(fakeBin.resolve("jpackage"), "#!/bin/sh\n")
            Files.writeString(fakeBin.resolve("jar"), "#!/bin/sh\n")
        }

        override fun exec(
            command: List<String>,
            workingDir: Path,
            env: Map<String, String>,
        ): Int {
            commands.add(command)
            return 0
        }

        /** `JdkResolver` 会拿它探版本；回一段空输出即可，不影响 jpackage 的定位。 */
        override fun spawn(
            command: List<String>,
            workingDir: Path,
            env: Map<String, String>,
        ): Process =
            ProcessBuilder("sh", "-c", "exit 1")
                .redirectErrorStream(true)
                .start()

        override fun which(name: String): Path? = fakeBin.resolve(name).takeIf { Files.isRegularFile(it) }

        fun lastCommand(): List<String> = commands.last()

        fun allCommands(): List<List<String>> = commands
    }
}
