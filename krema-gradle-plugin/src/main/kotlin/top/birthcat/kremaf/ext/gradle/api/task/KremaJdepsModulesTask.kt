/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.internal.tool.JdepsRunner
import java.nio.file.Path
import javax.inject.Inject

/**
 * Prints the runtime module list derived from jdeps, before any image is trimmed.
 *
 * Produces no files, so it extends [DefaultTask] directly; declaring build/krema as an output
 * would overlap with tasks that own that directory.
 */
@DisableCachingByDefault(because = "prints the derived module list only; produces no files")
abstract class KremaJdepsModulesTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description = "Print the runtime module list derived from jdeps, without trimming an image"
    }

    /** Application classpath used for module derivation. */
    @get:Classpath
    abstract val classpathFiles: ConfigurableFileCollection

    /** Directory of the jdeps executable from the JDK toolchain. */
    @get:Internal
    abstract val jdkBinDir: Property<String>

    /** Value passed to jdeps as --multi-release. */
    @get:Input
    abstract val multiRelease: Property<String>

    /** Injected exec operations. */
    @get:Inject
    abstract val execOperations: ExecOperations

    /** Prints the module list; fails when jdeps produced no usable output. */
    @Throws(GradleException::class)
    @TaskAction
    fun printModules() {
        val out =
            JdepsRunner(
                execOperations,
                Path.of(jdkBinDir.get()),
                multiRelease.get(),
                classpathFiles,
            ).run(verbose = false)
        val modules = out.firstLine()
        if (out.exitValue != 0 || modules.isEmpty()) {
            throw GradleException("Failed to derive runtime modules:\n${out.text}")
        }
        logger.lifecycle("Runtime Modules: $modules")
    }
}
