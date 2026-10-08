/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.nativeimpl

import top.birthcat.kremaf.ext.spi.iml.delegate.NativeLibraryFinder
import top.birthcat.kremaf.ext.spi.iml.delegate.Platform
import top.birthcat.kremaf.ext.spi.iml.delegate.PlatformDetector
import top.birthcat.kremaf.ext.spi.iml.delegate.executableSuffix
import top.birthcat.kremaf.ext.spi.port.Classpath
import top.birthcat.kremaf.ext.spi.port.CommandRunner
import top.birthcat.kremaf.ext.spi.port.FileSystem
import top.birthcat.kremaf.ext.spi.port.KremaProject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * `native-image` 的命令拼装与收尾。
 *
 * 命令形状对齐上游 `krema-cli` 的 `BuildCommand.buildNativeImage()`，classpath 来源不照抄：
 * 上游那句 `mvn dependency:build-classpath` 是 Maven 耦合，这里只吃 [Classpath]。
 *
 * 工具查找顺序固定为 `GRAALVM_HOME` → 解析出的 JDK → `PATH`，三级都由 [CommandRunner] 落子进程，
 * 测试里换掉 runner 就能在没有 GraalVM 的机器上断言完整命令行。
 */
class NativeImageBuilder(
    private val commandRunner: CommandRunner,
    private val fileSystem: FileSystem,
    private val nativeLibraryFinder: NativeLibraryFinder = NativeLibraryFinder(fileSystem),
) {
    /** 拼一条 `native-image` 命令。 */
    fun command(
        project: KremaProject,
        classpath: Classpath,
        classesDir: Path,
        outputBinary: Path,
        resourceConfig: Path?,
        tool: Path,
    ): List<String> {
        val mainClass = project.java.mainClass ?: error("krema.toml 的 [build].main_class 必须配，native 构建没有默认主类兜底")
        return buildList {
            add(tool.toString())
            add("-cp")
            add(libraryPath(classpath, classesDir))
            add("--enable-native-access=ALL-UNNAMED")
            add("-H:+UnlockExperimentalVMOptions")
            add("-H:-CheckToolchain")
            add("-Djava.awt.headless=true")
            if (PlatformDetector.detect() == Platform.DARWIN && PlatformDetector.getArch() == "aarch64") {
                add("-H:CCompilerOption=-arch")
                add("-H:CCompilerOption=arm64")
            }
            classpath.nativeLibDir?.let { add("-Djava.library.path=$it") }
            resourceConfig?.let { add("-H:ResourceConfigurationFiles=$it") }
            add("-o")
            add(outputBinary.toString())
            add(mainClass)
        }
    }

    /**
     * 找 `native-image`。
     *
     * @param javaHome 上一级解析出的 JDK，GraalVM 发行版就在它的 `bin` 下
     * @param env 环境变量快照，测试可注入
     */
    fun findTool(
        javaHome: Path?,
        env: Map<String, String> = System.getenv(),
    ): Path? {
        env["GRAALVM_HOME"]
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it).resolve("bin").resolve(toolName()) }
            ?.takeIf(::isExecutable)
            ?.let { return it }
        javaHome
            ?.resolve("bin")
            ?.resolve(toolName())
            ?.takeIf(::isExecutable)
            ?.let { return it }
        return commandRunner.which(NATIVE_IMAGE)
    }

    /** 前端资源清单：native-image 默认只打进 classpath 里的资源，assets 在输出目录，得显式声明。 */
    fun writeResourceConfig(
        project: KremaProject,
        workDir: Path,
    ): Path {
        val assetsPath = project.config.build.assetsPath
        val config =
            """
            {
              "resources": {
                "includes": [
                  { "pattern": "%s/.*" }
                ]
              }
            }
            """.trimIndent()
        val file = workDir.resolve("krema-resource-config.json")
        fileSystem.writeText(file, config.format(assetsPath))
        return file
    }

    /** 把原生库拷到可执行文件旁边，运行时才找得到。 */
    fun copyNativeLibrary(
        classpath: Classpath,
        outputBinary: Path,
    ): Path? {
        val candidates = classpath.entries.mapNotNull { it.parent } + listOfNotNull(classpath.nativeLibDir)
        val library = nativeLibraryFinder.findLibrary(candidates) ?: return null
        fileSystem.copyFlat(library, outputBinary.parent)
        return library
    }

    /**
     * Windows 上把 VC runtime 拷到产物旁，终端用户就不必再装 Visual C++ 可再发行组件。
     *
     * 来源按 `VCToolsRedistDir`（Developer 命令行里才有）→ `System32` 的顺序找，非 Windows 平台直接跳过。
     *
     * @return 实际拷过去的 DLL
     */
    fun copyVCRuntime(
        outputDir: Path,
        env: Map<String, String> = System.getenv(),
    ): List<Path> {
        if (PlatformDetector.detect() != Platform.WINDOWS) return emptyList()
        val sources = listOfNotNull(vcRedistDir(env), env["SYSTEMROOT"]?.let { Path.of(it, "System32") })
        for (source in sources) {
            val copied = copyDlls(source, outputDir)
            if (copied.isNotEmpty()) return copied
        }
        return emptyList()
    }

    private fun vcRedistDir(env: Map<String, String>): Path? {
        val redist = env["VCToolsRedistDir"]?.takeIf { it.isNotBlank() } ?: return null
        val x64 = Path.of(redist, "x64")
        return fileSystem
            .list(x64)
            .firstOrNull { it.fileName.toString().startsWith("Microsoft.VC") && it.fileName.toString().endsWith(".CRT") }
    }

    private fun copyDlls(
        sourceDir: Path,
        outputDir: Path,
    ): List<Path> {
        val present = fileSystem.list(sourceDir).map { it.fileName.toString() }.toSet()
        return VC_RUNTIME_DLLS.filter(present::contains).map { name ->
            fileSystem.copyFlat(sourceDir.resolve(name), outputDir)
            outputDir.resolve(name)
        }
    }

    private fun libraryPath(
        classpath: Classpath,
        classesDir: Path,
    ): String =
        (listOf(classesDir, classpath.classOutputDir) + classpath.entries)
            .distinct()
            .joinToString(File.pathSeparator) { it.toAbsolutePath().toString() }

    private fun isExecutable(path: Path): Boolean = Files.isExecutable(path)

    private fun toolName(): String = NATIVE_IMAGE + executableSuffix()

    private companion object {
        private const val NATIVE_IMAGE = "native-image"
        private val VC_RUNTIME_DLLS = listOf("vcruntime140.dll", "vcruntime140_1.dll")
    }
}
