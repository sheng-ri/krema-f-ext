/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.internal.tool.JdepsRunner
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * Runs jdeps -v and prints per-package dependency details, so the user can decide what to add to
 * krema.runtime.modules.
 *
 * A separate task rather than a switch on [KremaJdepsModulesTask]: jdeps rejects -verbose and
 * --print-module-deps when given together.
 */
@DisableCachingByDefault(because = "diagnostic report: a cache hit would skip the run and hide everything")
abstract class KremaJdepsModulesVerboseTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description =
            "Print per-package jdeps -v dependency details and write a report to build/krema/jdeps-verbose.txt"
    }

    /** Application classpath used for dependency inspection. */
    @get:Classpath
    abstract val classpathFiles: ConfigurableFileCollection

    /** Report destination; defaults to build/krema/jdeps-verbose.txt. */
    @get:Internal
    abstract val reportFile: RegularFileProperty

    /** Directory of the jdeps executable from the JDK toolchain. */
    @get:Internal
    abstract val jdkBinDir: Property<String>

    /** Value passed to jdeps as --multi-release. */
    @get:Input
    abstract val multiRelease: Property<String>

    /** Injected exec operations. */
    @get:Inject
    abstract val execOperations: ExecOperations

    /** Writes the report as raw bytes and echoes it to the console. */
    @TaskAction
    fun printVerbose() {
        val out =
            JdepsRunner(
                execOperations,
                Path.of(jdkBinDir.get()),
                multiRelease.get(),
                classpathFiles,
            ).run(verbose = true)
        // 原样落盘：经字符串中转会按 Gradle 默认编码改写非 UTF-8 报告。
        val target = reportFile.get().asFile.toPath()
        target.parent?.let(Files::createDirectories)
        Files.write(target, out.bytes)
        if (out.exitValue != 0) {
            // -v 可能一边非零退出一边产出可用报告，报告正是本 task 的目的。
            logger.warn("jdeps -v exited with code ${out.exitValue}; details written to ${target.toAbsolutePath()}")
        }
        logger.lifecycle(out.text)
    }
}
