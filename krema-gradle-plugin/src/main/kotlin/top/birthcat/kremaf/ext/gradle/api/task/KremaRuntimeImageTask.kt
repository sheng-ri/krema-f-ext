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
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.api.Platform
import top.birthcat.kremaf.ext.gradle.internal.tool.JdepsRunner
import top.birthcat.kremaf.ext.gradle.internal.tool.JlinkTool
import java.nio.file.Path
import javax.inject.Inject

/**
 * Derives the modules the application uses with jdeps, then trims a minimal runtime image with
 * jlink for jpackage's --runtime-image option.
 *
 * The module list has two sources: by default jdeps derives it from [classpathFiles]; a non-empty
 * [inputModules], set through krema.runtime.modules, replaces the derivation.
 */
@CacheableTask
abstract class KremaRuntimeImageTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description = "Derive modules via jdeps and trim a minimal runtime image with jlink (build/krema/runtime)"
    }

    /** Application classpath used for module derivation; compared by content. */
    @get:Classpath
    abstract val classpathFiles: ConfigurableFileCollection

    /**
     * Explicit module list from krema.runtime.modules; a non-empty set replaces jdeps derivation.
     *
     * Because Gradle has no conditional inputs, a classpath change still reruns jlink when the
     * list is explicit. An onlyIf switch would disable the explicit path entirely.
     */
    @get:Input
    abstract val inputModules: SetProperty<String>

    /** Runtime image directory, conventionally build/krema/runtime. */
    @get:OutputDirectory
    abstract val outputRuntimeImageDir: DirectoryProperty

    /** Directory of the jdeps and jlink executables from the JDK toolchain. */
    @get:Input
    abstract val jdkBinDir: Property<String>

    /** Host platform; part of the cache key because runtime images are not portable. */
    @get:Input
    abstract val hostPlatform: Property<Platform>

    /**
     * Value passed to jdeps as --multi-release.
     *
     * Follows the bytecode target rather than the toolchain: multi-release jars are read for the
     * same release the classes were compiled for, otherwise the derived list is unusable.
     */
    @get:Input
    abstract val multiRelease: Property<String>

    /** Injected exec operations. */
    @get:Inject
    abstract val execOperations: ExecOperations

    /** Computes the module list and carves the runtime image. */
    @Throws(GradleException::class)
    @TaskAction
    fun createRuntimeImage() {
        val binDir = Path.of(jdkBinDir.get())
        val explicit = inputModules.get()
        // 排序：SetProperty 的指纹对顺序敏感，不排序会偶发重跑 jlink。
        val moduleList = if (explicit.isEmpty()) jdepsModules() else explicit.sorted().joinToString(",")
        JlinkTool(execOperations).carve(binDir, moduleList, outputRuntimeImageDir.get().asFile.toPath())
    }

    /** 取 classpath 依赖的 JDK 模块，逗号分隔。 */
    private fun jdepsModules(): String {
        val out =
            JdepsRunner(
                execOperations = execOperations,
                binDir = Path.of(jdkBinDir.get()),
                multiRelease = multiRelease.get(),
                classpathFiles = classpathFiles,
            ).run(verbose = false)
        val modules = out.firstLine()
        if (out.exitValue != 0 || modules.isEmpty()) {
            throw GradleException("Failed to derive runtime modules:\n${out.text}")
        }
        return modules
    }
}
