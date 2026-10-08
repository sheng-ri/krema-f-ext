/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import top.birthcat.kremaf.ext.spi.iml.delegate.JdkResolver
import top.birthcat.kremaf.ext.spi.iml.delegate.PlatformDetector
import top.birthcat.kremaf.ext.spi.iml.delegate.executableSuffix
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NativeImageBuilder
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioFileSystem
import top.birthcat.kremaf.ext.spi.iml.nativeimpl.NioJavaCompiler
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.JavaProject
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.io.File
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
        val classesDir = dir.resolve("out/classes")
        assertEquals(toolIn(graalHome).toString(), native.first())
        assertEquals("-cp", native[1])
        assertEquals(
            listOf(classesDir.toString(), bogusOutput.toString(), jar.toString()),
            native[2].split(File.pathSeparator),
        )
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

    /** 造一个装着可执行 `native-image` 的假工具目录。 */
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
