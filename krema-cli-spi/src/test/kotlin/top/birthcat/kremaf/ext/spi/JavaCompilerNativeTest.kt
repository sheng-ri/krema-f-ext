/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import top.birthcat.kremaf.ext.spi.iml.delegate.JdkResolver
import top.birthcat.kremaf.ext.spi.iml.delegate.PlatformDetector
import top.birthcat.kremaf.ext.spi.iml.delegate.executableSuffix
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.ArgFiles
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NativeImageBuilder
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioFileSystem
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioJavaCompiler
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.JavaProject
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * `compileNative` 的命令形状验证：假 [CommandRunner] 记下每一次调用，断言参数与工具查找顺序。
 *
 * 真 `native-image` 不进 CI 门禁（镜像里没有 GraalVM），所以这一层把「拼对命令」当成验收主体。
 */
class JavaCompilerNativeTest {
    private val fs = NioFileSystem()

    @Test
    fun assemblesNativeImageCommandShape(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val graalHome = fakeToolDir(dir.resolve("graalvm"))
        val runner = RecordingRunner()
        val bogusOutput = dir.resolve("gradle-class-output").also { fs.writeText(it.resolve("Main.class"), "x") }
        val jar = dir.resolve("app.jar").also { fs.writeText(it, "jar") }
        val libDir = dir.resolve("lib").also { fs.writeText(it.resolve(PlatformDetector.formatLibraryName("webview")), "so") }
        val project = KremaProject(dir, java = JavaProject(mainClass = "com.example.Main"))

        compiler(runner, dir, graalHome).compileNative(project, Classpath(listOf(jar), bogusOutput, libDir))

        val native = runner.commands.single { it.first().endsWith("native-image") }
        assertEquals(toolIn(graalHome).toString(), native.first())
        assertTrue(native.contains("--enable-native-access=ALL-UNNAMED"))
        assertTrue(native.contains("-H:+UnlockExperimentalVMOptions"))
        assertTrue(native.contains("-H:-CheckToolchain"))
        assertTrue(native.contains("-Djava.awt.headless=true"))
        assertTrue(native.contains("-Djava.library.path=$libDir"))
        assertEquals(1, native.count { it.startsWith("-H:ResourceConfigurationFiles=") })
        assertEquals("-o", native[native.size - 3])
        assertEquals(project.bundle.appName, Path.of(native[native.size - 2]).fileName.toString())
        assertEquals("com.example.Main", native.last())
    }

    @Test
    fun keepsShortClasspathOnTheCommandLine(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val graalHome = fakeToolDir(dir.resolve("graalvm"))
        val runner = RecordingRunner()
        val bogusOutput = dir.resolve("gradle-class-output")
        val jar = dir.resolve("app.jar").also { fs.writeText(it, "jar") }
        val project = KremaProject(dir, java = JavaProject(mainClass = "com.example.Main"))

        compiler(runner, dir, graalHome).compileNative(project, Classpath(listOf(jar), bogusOutput))

        val native = runner.commands.single { it.first().endsWith("native-image") }
        assertFalse(native.any { it.startsWith("@") }, native.toString())
        assertEquals("-cp", native[1])
        assertEquals(
            listOf(dir.resolve("out/classes").toString(), bogusOutput.toString(), jar.toString()),
            native[2].split(File.pathSeparator),
        )
    }

    /**
     * classpath 一长就换成参数文件。
     *
     * 门限按 `CreateProcess` 的 32767 字符定。这里不靠超长文件名（路径总长本身有上限），
     * 而是堆足够多的条目把拼出来的 classpath 撑过门限。
     */
    @Test
    fun passesLongClasspathThroughAnArgFile(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val graalHome = fakeToolDir(dir.resolve("graalvm"))
        val runner = RecordingRunner()
        val entries = List(1_200) { index -> dir.resolve("deps/dep-$index.jar").also { fs.writeText(it, "jar") } }
        val bogusOutput = dir.resolve("gradle-class-output")
        val project = KremaProject(dir, java = JavaProject(mainClass = "com.example.Main"))
        val classpath = Classpath(entries, bogusOutput)

        compiler(runner, dir, graalHome).compileNative(project, classpath)

        val native = runner.commands.single { it.first().endsWith("native-image") }
        assertFalse(native.contains("-cp"), "classpath 超长时命令行里不该再有 -cp")
        assertTrue(native[1].startsWith("@"), native.toString())
        val argFile = Path.of(native[1].removePrefix("@"))
        assertEquals(dir.resolve("out/krema-classpath${ArgFiles.SUFFIX}"), argFile)
        assertEquals(
            listOf("-cp", (listOf(dir.resolve("out/classes"), bogusOutput) + entries).joinToString(File.pathSeparator)),
            readArguments(argFile),
        )
        assertEquals("com.example.Main", native.last())
    }

    /**
     * 参数文件里含空白或引号的路径要有引号兜住。
     *
     * `JLI_Launch` 是空白分隔 token 的，不加引号的话 `C:/Program Files/...` 会被劈成两个参数，
     * 报出来的错会是「找不到主类 x86」这种与病因无关的东西。
     */
    @Test
    fun quotesPathsWithSpacesInTheArgFile(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val graalHome = fakeToolDir(dir.resolve("graalvm"))
        val runner = RecordingRunner()
        val entries = List(1_200) { index -> dir.resolve("with space-$index/dep-$index.jar").also { fs.writeText(it, "jar") } }
        val project = KremaProject(dir, java = JavaProject(mainClass = "com.example.Main"))

        compiler(runner, dir, graalHome).compileNative(project, Classpath(entries, dir.resolve("out/classes")))

        val native = runner.commands.single { it.first().endsWith("native-image") }
        assertTrue(native[1].startsWith("@"), native.take(3).toString())
        val argFile = Path.of(native[1].removePrefix("@"))
        val arguments = readArguments(argFile)

        assertEquals("-cp", arguments.first())
        assertEquals(
            (listOf(dir.resolve("out/classes")) + entries).joinToString(File.pathSeparator),
            arguments[1],
            "含空白的 classpath 在参数文件里要作为一个整体",
        )
    }

    @Test
    fun compilesSourcesBeforeNativeImage(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val graalHome = fakeToolDir(dir.resolve("graalvm"))
        val runner = RecordingRunner()
        val src = dir.resolve("src-java/com/example").also { fs.writeText(it.resolve("Main.java"), "class Main {}") }
        val project = KremaProject(dir, java = JavaProject(mainClass = "com.example.Main", sourceDir = dir.resolve("src-java")))

        compiler(runner, dir, graalHome).compileNative(project, Classpath(emptyList(), dir.resolve("out/classes")))

        val javac = runner.commands.first()
        assertTrue(javac.first().endsWith("javac"), javac.toString())
        assertTrue(javac.contains(src.resolve("Main.java").toString()), javac.toString())
        assertTrue(
            runner.commands
                .last()
                .first()
                .endsWith("native-image"),
        )
    }

    @Test
    fun findsToolInGraalvmJdkThenPathOrder(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val onPath = dir.resolve("path/native-image")
        val builder = NativeImageBuilder(RecordingRunner(pathHit = onPath), fs)
        val jdk = fakeToolDir(dir.resolve("jdk25"))
        val graal = fakeToolDir(dir.resolve("graalvm"))

        // 三级顺序：GRAALVM_HOME 压过 JDK，JDK 压过 PATH。
        assertEquals(toolIn(graal), builder.findTool(jdk, mapOf("GRAALVM_HOME" to graal.toString())))
        assertEquals(toolIn(jdk), builder.findTool(jdk, emptyMap()))
        assertEquals(onPath, builder.findTool(null, emptyMap()))
        assertEquals(onPath, builder.findTool(dir.resolve("no-such-jdk"), mapOf("GRAALVM_HOME" to dir.resolve("no-such-graal").toString())))
        assertNull(NativeImageBuilder(RecordingRunner(pathHit = null), fs).findTool(dir.resolve("no-such-jdk"), emptyMap()))
    }

    @Test
    fun copiesNativeLibraryBesideTheBinary(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val graalHome = fakeToolDir(dir.resolve("graalvm"))
        val runner = RecordingRunner()
        val name = PlatformDetector.formatLibraryName("webview")
        val libDir = dir.resolve("lib").also { fs.writeText(it.resolve(name), "so") }
        val project = KremaProject(dir, java = JavaProject(mainClass = "com.example.Main"))

        val binary = compiler(runner, dir, graalHome).compileNative(project, Classpath(emptyList(), dir.resolve("out/classes"), libDir))

        assertEquals("so", fs.readText(binary.parent.resolve(name)))
    }

    /**
     * 候选目录里同名库取先命中的那个。
     *
     * 落盘后只有一份文件，所以「先到先得」不是取舍而是唯一说得通的行为 ——
     * 后到的会静默盖掉前面的，而 JVM 也只会加载先找到的那份。
     */
    @Test
    fun keepsFirstNativeLibraryOnNameCollision(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val builder = NativeImageBuilder(RecordingRunner(), fs)
        val name = PlatformDetector.formatLibraryName("webview")
        val first = dir.resolve("first").also { fs.writeText(it.resolve(name), "first") }
        val second = dir.resolve("second").also { fs.writeText(it.resolve(name), "second") }
        fs.writeText(first.resolve("a.jar"), "jar")
        fs.writeText(second.resolve("b.jar"), "jar")
        val binary = outBinary(dir, "krema-app")

        val copied =
            builder.copyNativeLibrary(
                Classpath(listOf(first.resolve("a.jar"), second.resolve("b.jar")), dir.resolve("classes")),
                binary,
            )

        assertEquals(listOf(first.resolve(name)), copied)
        assertEquals("first", fs.readText(binary.parent.resolve(name)))
    }

    /**
     * 候选目录里有几个**不同名**的库就拷几个。
     *
     * `java.library.path` 天生是「目录里可能有多个 `.so`」的语义，捞到第一个就收工会漏。
     */
    @Test
    fun copiesEveryDistinctNativeLibraryBesideTheBinary(
        @TempDir root: File,
    ) {
        val dir = root.toPath()
        val builder = NativeImageBuilder(RecordingRunner(), fs)
        val libDir = dir.resolve("lib")
        val names = listOf("webview", "extra").map(PlatformDetector::formatLibraryName)
        names.forEach { fs.writeText(libDir.resolve(it), it) }
        val binary = outBinary(dir, "krema-app")

        val copied =
            builder.copyNativeLibrary(
                Classpath(listOf(dir.resolve("a.jar")), dir.resolve("classes"), libDir),
                binary,
            )

        assertEquals(names.map(libDir::resolve), copied)
        names.forEach { assertEquals(it, fs.readText(binary.parent.resolve(it))) }
    }

    /**
     * 把参数文件切回 token。
     *
     * [ArgFiles.render] 保证一行一个 token，所以按行切、剥掉最外层成对引号即可 ——
     * 这里刻意不复刻 launcher 的空白分词，免得测的是自己的正则。
     */
    private fun readArguments(file: Path): List<String> =
        Files
            .readString(file)
            .lineSequence()
            .filter(String::isNotEmpty)
            .map { it.removeSurrounding("\"") }
            .toList()

    /** 建一个假的可执行文件路径，父目录就是产物目录。 */
    private fun outBinary(
        dir: Path,
        name: String,
    ): Path = dir.resolve("out/$name").also { fs.writeText(it, "bin") }

    /**
     * 造一个装着可执行 `native-image` 的假工具目录。
     *
     * 只看名字，不看是不是真 GraalVM —— 有一条记录命令行就够，本机不需要装 GraalVM。
     */
    private fun fakeToolDir(home: Path): Path {
        val tool = toolIn(home)
        fs.writeText(tool, "bin")
        fs.markExecutable(tool)
        return home
    }

    private fun toolIn(home: Path): Path = home.resolve("bin").resolve("native-image" + executableSuffix())

    /**
     * 装配一个把 `KREMA_JAVA_HOME` 与 `GRAALVM_HOME` 都指到临时目录的编译器。
     *
     * `KREMA_JAVA_HOME` 那一档 `JdkResolver` 只判可执行、不探版本，所以塞个空 `bin/java` 就够，
     * 测试不必真装 JDK 25。
     */
    private fun compiler(
        runner: CommandRunner,
        dir: Path,
        graalHome: Path = dir.resolve("graalvm"),
    ): NioJavaCompiler {
        val fakeJdk =
            dir.resolve("jdk25").also { home ->
                fs.writeText(home.resolve("bin/java"), "")
                fs.markExecutable(home.resolve("bin/java"))
            }
        return NioJavaCompiler(
            runner,
            JdkResolver(runner),
            dir.resolve("out"),
            fs,
            mapOf("KREMA_JAVA_HOME" to fakeJdk.toString(), "GRAALVM_HOME" to graalHome.toString()),
        )
    }

    private companion object {
        /** 单个 classpath 条目撑到 30000 字符上，必然超过命令行门限；文件名本身得留在 255 字节内。 */
        private val PADDING_LONG = "x".repeat(20_000)
    }

    /** 只记录命令，不起真子进程。 */
    private class RecordingRunner(
        private val pathHit: Path? = null,
    ) : CommandRunner {
        val commands = mutableListOf<List<String>>()

        override fun exec(
            command: List<String>,
            workingDir: Path,
            env: Map<String, String>,
        ): Int {
            commands.add(command)
            return 0
        }

        override fun spawn(
            command: List<String>,
            workingDir: Path,
            env: Map<String, String>,
        ): Process = error("本测试不需要长驻进程")

        override fun which(name: String): Path? = pathHit.takeIf { name == "native-image" }
    }
}
