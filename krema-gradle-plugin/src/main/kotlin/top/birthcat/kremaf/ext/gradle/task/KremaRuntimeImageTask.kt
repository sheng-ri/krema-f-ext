/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.domain.executableSuffix
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.inject.Inject

/**
 * 用 jdeps 推导应用实际用到的模块，再用 jlink 裁出最小运行时镜像，供 jpackage 的 `--runtime-image` 使用。
 *
 * 模块清单有两条路：默认由 jdeps 按 [classpathFiles] 现算（推导结果**不**配成输入 —— 它完全由
 * classpath 决定，多声明一份只会算出两份互相矛盾的模块名）；`krema { runtime { modules } }` 非空时
 * [modules] 取代推导，作为覆盖值的唯一落点。
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

    /**
     * 手动指定的模块清单，来自 `krema { runtime { modules } }`；非空时取代 jdeps 推导。
     *
     * 显式指定时 [classpathFiles] 变动仍会重跑 jlink：Gradle 没有「输入条件化」的手段，而按
     * `modules.isEmpty()` 加 `onlyIf` 会让显式指定时 jlink 永不执行，正好把方向搞反。
     */
    @get:Input
    abstract val modules: SetProperty<String>

    /** 运行时镜像目录，约定为 `outputDir/runtime`（即 `build/krema/runtime`）。 */
    @get:OutputDirectory
    abstract val runtimeImageDir: DirectoryProperty

    /** jdeps 与 jlink 所在的可执行文件目录，取自 JDK toolchain。 */
    @get:Internal
    abstract val jdkBinDir: Property<String>

    /**
     * jdeps 的 `--multi-release` 版本，取编译产物的目标字节码主版本号。
     *
     * 跟字节码目标走而不是跟 toolchain 走：jdeps 读的 class 是按目标版本编译出来的，多发行版 jar 的
     * `META-INF/versions/N` 也要按同一档解析；两者可以不同，按别的版本号读会算出一份用不上的清单。
     */
    @get:Input
    abstract val multiRelease: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun createRuntimeImage() {
        val binDir = Path.of(jdkBinDir.get())
        val explicit = modules.get()
        // 排序后交给 jlink：SetProperty 的指纹对迭代顺序敏感，用户从 Map 派生集合时顺序可能变，
        // 不排序会让同一份清单偶发地重跑 jlink。
        val moduleList = if (explicit.isEmpty()) jdepsModules(binDir) else explicit.sorted().joinToString(",")
        jlink(binDir, moduleList)
    }

    /** 取应用 classpath 依赖到的 JDK 模块，逗号分隔，形如 `java.base,java.desktop`。 */
    private fun jdepsModules(binDir: Path): String {
        val out = jdepsRunner().run(verbose = false)
        val modules = out.firstLine()
        if (out.exitValue != 0 || modules.isEmpty()) {
            throw GradleException("推导运行时模块失败：\n${out.text}")
        }
        return modules
    }

    private fun jdepsRunner(): JdepsRunner =
        JdepsRunner(
            execOperations = execOperations,
            binDir = Path.of(jdkBinDir.get()),
            multiRelease = multiRelease.get(),
            classpathFiles = classpathFiles,
        )

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
