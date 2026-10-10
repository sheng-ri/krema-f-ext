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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.KremaProjectTask
import javax.inject.Inject

/**
 * 跑前端生产构建（npm 命令）并把 `dist` 落为声明式输出。
 *
 * 输入（`package.json` + 锁文件）与输出（`dist` 目录）都声明出来，因此能命中构建缓存，
 * 输入没变时第二次执行是 UP-TO-DATE。命令走 Gradle 自己的 [ExecOperations]，不另造执行口，
 * 失败时把命令自身的 stdout/stderr 一并抛给用户，省去猜是哪一步挂了。
 *
 * 不继承父类的 `outputDir` 作为产物：本 task 真正的产物是嵌套其下的 [distDir]，
 * 同一 task 不允许出现「父目录 + 子目录」两份输出，故把 [outputDir] 改标成 [Internal]。
 */
@CacheableTask
abstract class KremaFrontendBuildTask : KremaProjectTask() {
    /** 产物根目录仅作 [distDir] 的基准，本身不是本 task 的产物。 */
    @get:Internal
    abstract override val outputDir: DirectoryProperty

    /** 前端生产构建命令，如 `npm run build`。 */
    @get:Input
    abstract val buildCommand: Property<String>

    /**
     * 前端依赖安装命令，如 `pnpm install`；不配就不装。
     *
     * 用户自己声明用哪个包管理器，插件不猜 —— 猜错的表现是「装完了但构建还是找不到 vite」，
     * 比不装更难查。
     */
    @get:Input
    @get:Optional
    abstract val installCommand: Property<String>

    /** 前端输入：项目根的 `package.json` 与存在的锁文件。缺失的候选不在集合里。 */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val frontendInputs: ConfigurableFileCollection

    /**
     * 前端构建产物目录，取 `krema.toml` 的 `out_dir`（默认 `dist/`，相对项目根）。
     *
     * 就是前端工具链自己的落点：命令是用户给的，插件无权把它改道到别处，
     * 因此这里只声明这个目录为输出，不替用户决定它该落在哪。
     */
    @get:OutputDirectory
    abstract val distDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun build() {
        // 先装依赖再构建：删干净仓库后 node_modules 并不存在，直接构建会在「vite 找不到」这种
        // 与配置无关的地方炸掉。没配 installCommand 就跳过，那时用户自己负责把依赖准备好。
        installCommand.orNull?.let { install(it) }
        run(buildCommand.get(), "前端构建")
    }

    private fun install(command: String) = run(command, "前端依赖安装")

    private fun run(
        command: String,
        label: String,
    ) {
        val captured = java.io.ByteArrayOutputStream()
        val result =
            execOperations.exec { spec ->
                spec.commandLine(command.trim().split(Regex("\\s+")))
                spec.workingDir(projectDir.get().asFile)
                spec.isIgnoreExitValue = true
                spec.standardOutput = captured
                spec.errorOutput = captured
            }
        if (result.exitValue != 0) {
            throw GradleException("$label 失败（命令：$command）：\n$captured")
        }
    }
}
