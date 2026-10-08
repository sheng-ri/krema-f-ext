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
 *
 * classpath 不拼进命令行：写进 [ArgFiles] 的参数文件，命令行只递 `@<文件>`。
 * Windows 的 `CreateProcess` 上限约 32767 字符，而 classpath 拉长到 32K 是常态 ——
 * 真吐出去只会换回一个看不懂的失败，所以在拼装这一层就掐掉。
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
        classpathArgFile: Path? = null,
    ): List<String> {
        val mainClass = project.java.mainClass ?: error("krema.toml 的 [build].main_class 必须配，native 构建没有默认主类兜底")
        return buildList {
            add(tool.toString())
            if (classpathArgFile == null) {
                add("-cp")
                add(libraryPath(classpath, classesDir))
            } else {
                add("@$classpathArgFile")
            }
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
     * 把 classpath 写进参数文件，返回 `@` 引用的那个文件。
     *
     * 只有超长才值得走文件 —— 门限之内命令行更直观，出问题时肉眼就能看出参数拼错没有。
     * 超长时命令行里那两段（`-cp` 与值）换成 `@<文件>`，省下的正是 classpath 那一大坨。
     */
    fun classpathArgFile(
        classpath: Classpath,
        classesDir: Path,
        workDir: Path,
    ): Path? {
        val path = libraryPath(classpath, classesDir)
        if (path.length <= COMMAND_LINE_LIMIT_CHARS) return null
        val file = workDir.resolve(CLASSPATH_ARG_FILE)
        fileSystem.writeText(file, ArgFiles.render(listOf("-cp", path)))
        return file
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

    /**
     * 前端资源清单：native-image 默认只打进 classpath 里的资源，assets 在输出目录，得显式声明。
     *
     * `assetsPath` 是用户填的（`krema.toml`），Windows 路径里带 `\` 或目录名里带 `"` 都正常，
     * 所以拼进 JSON 前先转义 —— 用 `String.format` 硬拼会让清单变成非法 JSON，
     * 而 native-image 报的错会离现场很远（它只会说读不了这份清单）。
     */
    fun writeResourceConfig(
        project: KremaProject,
        workDir: Path,
    ): Path {
        val pattern = jsonString(project.config.build.assetsPath + "/.*")
        val config =
            """
            {
              "resources": {
                "includes": [
                  { "pattern": $pattern }
                ]
              }
            }
            """.trimIndent()
        val file = workDir.resolve("krema-resource-config.json")
        fileSystem.writeText(file, config)
        return file
    }

    /** 按 JSON 字符串字面量转义：只处理 `\`、`"` 与控制字符，够路径用。 */
    private fun jsonString(value: String): String {
        val escaped =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .map { if (it < ' ') "\\u%04x".format(it.code) else it.toString() }
                .joinToString("")
        return "\"$escaped\""
    }

    /**
     * 把原生库拷到可执行文件旁边，运行时才找得到。
     *
     * 一个目录里可能有多个 `.so`，所以按候选顺序全部拷过去，而不是捞到第一个就收工。
     * 先命中的目录先拷，同名文件按 Java 的绑定语义「先到先得」，后面的不再覆盖。
     *
     * @return 实际拷过去的库文件
     */
    fun copyNativeLibrary(
        classpath: Classpath,
        outputBinary: Path,
    ): List<Path> {
        val candidates = classpath.entries.mapNotNull { it.parent } + listOfNotNull(classpath.nativeLibDir)
        val copied = mutableListOf<Path>()
        val taken = mutableSetOf<String>()
        for (candidate in nativeLibraryFinder.findLibraries(candidates)) {
            if (!taken.add(candidate.fileName.toString())) continue
            fileSystem.copyFlat(candidate, outputBinary.parent)
            copied.add(candidate)
        }
        return copied
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
        private const val CLASSPATH_ARG_FILE = "krema-classpath" + ArgFiles.SUFFIX

        /** `CreateProcess` 的命令行上限是 32767 个 UTF-16 单元，留一点余量给别的参数。 */
        private const val COMMAND_LINE_LIMIT_CHARS = 30_000
        private val VC_RUNTIME_DLLS = listOf("vcruntime140.dll", "vcruntime140_1.dll")
    }
}
