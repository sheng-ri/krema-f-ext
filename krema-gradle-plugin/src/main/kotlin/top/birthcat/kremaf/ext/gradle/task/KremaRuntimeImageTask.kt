/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.KremaProjectTask
import top.birthcat.kremaf.ext.gradle.executableSuffix
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.inject.Inject

/**
 * 用 jdeps 推导应用实际用到的模块，再用 jlink 裁出最小运行时镜像，供 jpackage 的 `--runtime-image` 使用。
 *
 * 模块列表在 action 里现算而不是配成输入：它完全由 [classpathFiles] 决定，多声明一份只会让
 * classpath 变动时算出两份互相矛盾的模块名。
 *
 * 工具路径取自 JDK toolchain 而非 PATH：用户用别的 JDK 构建时，PATH 上的 jlink 可能根本不存在。
 */
@CacheableTask
abstract class KremaRuntimeImageTask : KremaProjectTask() {
    /** 产物根目录仅作 [runtimeImageDir] 的基准，本身不是本 task 的产物。 */
    @get:Internal
    abstract override val outputDir: DirectoryProperty

    /** 应用运行所需的 classpath，模块推导的输入。声明成 `@Classpath` 才能按内容而非路径判变更。 */
    @get:Classpath
    abstract val classpathFiles: ConfigurableFileCollection

    /** 运行时镜像目录，约定为 `outputDir/runtime`（即 `build/krema/runtime`）。 */
    @get:OutputDirectory
    abstract val runtimeImageDir: DirectoryProperty

    /** jdeps 与 jlink 所在的可执行文件目录，取自 JDK toolchain。 */
    @get:Internal
    abstract val jdkBinDir: Property<String>

    /**
     * jdeps 的 `--multi-release` 版本，取编译用的 JDK 主版本号。
     *
     * 跟 toolchain 走而不是写死：jlink 出的运行时就是 toolchain 那个版本，按别的版本号推导模块
     * 会算出运行时里根本不存在的模块。
     */
    @get:Input
    abstract val multiRelease: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun createRuntimeImage() {
        val binDir = Path.of(jdkBinDir.get())
        val modules = jdepsModules(binDir)
        jlink(binDir, modules)
    }

    /** 取应用 classpath 依赖到的 JDK 模块，逗号分隔，形如 `java.base,java.desktop`。 */
    private fun jdepsModules(binDir: Path): String {
        // jdeps 只认路径形式的输入：传 -cp 再给类名会报「路径不存在」并失败。
        val paths = classpathFiles.files.filter { it.exists() }.map { it.absolutePath }
        val captured = ByteArrayOutputStream()
        val exit =
            execOperations
                .exec { spec ->
                    // --multi-release 必须给，且必须与 toolchain 版本一致：jackson 这类依赖是
                    // 多发行版 jar，不给这个选项 jdeps 直接拒读，模块推导就废了。
                    spec.commandLine(
                        listOf(
                            tool(binDir, "jdeps"),
                            "--print-module-deps",
                            "--ignore-missing-deps",
                            "--multi-release",
                            multiRelease.get(),
                        ) + paths,
                    )
                    spec.isIgnoreExitValue = true
                    spec.standardOutput = captured
                    spec.errorOutput = captured
                }.exitValue
        val modules =
            captured
                .toString()
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
        if (exit != 0 || modules.isEmpty()) {
            throw GradleException("推导运行时模块失败：\n$captured")
        }
        return modules
    }

    private fun jlink(
        binDir: Path,
        modules: String,
    ) {
        val target = runtimeImageDir.get().asFile
        // Gradle 在 action 前就把 @OutputDirectory 建出来，而 jlink 见到已存在的目录就报「目录已存在」。
        // 该目录是本 task 声明的产物，重建前清空是安全的。
        target.deleteRecursively()
        val captured = ByteArrayOutputStream()
        val exit =
            execOperations
                .exec { spec ->
                    spec.commandLine(tool(binDir, "jlink"), "--add-modules", modules, "--output", target.absolutePath)
                    spec.isIgnoreExitValue = true
                    spec.standardOutput = captured
                    spec.errorOutput = captured
                }.exitValue
        if (exit != 0) {
            throw GradleException("裁剪运行时镜像失败（模块：$modules）：\n$captured")
        }
    }

    private fun tool(
        binDir: Path,
        name: String,
    ): String = binDir.resolve(name + executableSuffix()).toString()
}
