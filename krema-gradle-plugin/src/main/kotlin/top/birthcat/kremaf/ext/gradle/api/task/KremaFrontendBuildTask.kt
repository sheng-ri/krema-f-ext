/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.internal.domain.commandLineFor
import top.birthcat.kremaf.ext.gradle.internal.tool.capture
import javax.inject.Inject

/**
 * Runs the frontend production build and declares its output directory as a cacheable artifact.
 *
 * Inputs are the source and lock files matched under [inputFrontendDir], so a second run with
 * unchanged inputs is UP-TO-DATE. A failed command rethrows its own output, which points at the
 * failing step instead of a generic Gradle error.
 */
@CacheableTask
abstract class KremaFrontendBuildTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        // 落点由前端工具链决定，插件只能按配置读取。
        description =
            "Run the frontend production build (npm); output dir from krema.toml out_dir, declared as cacheable"
    }

    /** Frontend output directory, taken from out_dir or frontend_out_dir. */
    @get:OutputDirectory
    abstract val outputFrontendDir: DirectoryProperty

    /** Production build command, e.g. npm run build. */
    @get:Input
    abstract val inputBuildCommand: Property<String>

    /**
     * Frontend project directory: base for the input files and the command working directory.
     *
     * Marked [Internal] rather than @InputDirectory: hashing the whole tree would include
     * node_modules and defeat both up-to-date checks and the build cache. Moving the directory
     * still changes the output location, so staleness is detected anyway.
     */
    @get:Internal
    abstract val inputFrontendDir: DirectoryProperty

    /** Dependency install command; absent means dependencies must already be installed. */
    @get:Input
    abstract val inputInstallCommand: Property<String>

    /** Frontend sources and lock files used as cache inputs. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val frontendInputs: ConfigurableFileCollection

    /** Injected exec operations. */
    @get:Inject
    abstract val execOperations: ExecOperations

    /** Installs dependencies when configured, then runs the production build. */
    @Throws(GradleException::class)
    @TaskAction
    fun build() {
        // 目录不存在时直接报配置项，否则只会得到 Cannot run program 这类泛化错误。
        val front = inputFrontendDir.get().asFile
        if (!front.isDirectory) {
            throw GradleException(
                "Frontend directory does not exist: $front (configure it via krema.frontend.dir or frontend_dir in krema.toml)",
            )
        }
        // 先装依赖：干净仓库里没有 node_modules，直接构建会死在 vite 找不到。
        inputInstallCommand.orNull?.let { install(it) }
        run(inputBuildCommand.get(), "Frontend build")
    }

    private fun install(command: String) = run(command, "Frontend dependency install")

    private fun run(
        command: String,
        label: String,
    ) {
        val front = inputFrontendDir.get().asFile
        val result = execOperations.capture(commandLineFor(command), workingDir = front.toPath())
        if (result.exitValue != 0) {
            throw GradleException("$label failed (command: $command):\n${result.output}")
        }
    }
}
