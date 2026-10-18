/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.api.BundleSettings
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.api.Platform
import top.birthcat.kremaf.ext.gradle.internal.config.BUNDLE_LIBS
import top.birthcat.kremaf.ext.gradle.internal.tool.JpackageTool
import java.nio.file.Path
import javax.inject.Inject

/**
 * Builds the uncompressed, runnable app-image under build/krema/app with the jlink-trimmed runtime.
 *
 * Every platform package reuses this app-image instead of running jpackage again. Cacheable per
 * JDK and host platform: the same inputs on the same host reuse the previous output.
 */
@CacheableTask
abstract class KremaBundleTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description = "Build a runnable, uncompressed app-image (build/krema/app) with the jlink-trimmed runtime"
    }

    /** Root of the Krema output tree; the dependency directory lives here. */
    @get:Internal
    abstract val buildDir: DirectoryProperty

    /** Directory jpackage writes the app-image into. */
    @get:OutputDirectory
    abstract val outputBundleDir: DirectoryProperty

    /** Naming inputs; values from the extension already override krema.toml. */
    @get:Nested
    abstract val bundleSettings: Property<BundleSettings>

    /** Trimmed runtime passed to jpackage as --runtime-image; compared by content. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputRuntimeImageDir: DirectoryProperty

    /** Application, assets and runtime dependency jars copied into the dependency directory. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJarDeps: ConfigurableFileCollection

    /** Directory of the jpackage executable from the JDK toolchain. */
    @get:Input
    abstract val jdkBinDir: Property<String>

    /** Host platform; part of the cache key because bundles are not portable across platforms. */
    @get:Input
    abstract val hostPlatform: Property<Platform>

    /** Injected exec operations. */
    @get:Inject
    abstract val execOperations: ExecOperations

    /** Injected file system operations. */
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    /** Builds the app-image from the collected jars and the trimmed runtime. */
    @Throws(GradleException::class)
    @TaskAction
    fun bundle() {
        val platform = hostPlatform.get()
        val settings = bundleSettings.get()
        val bundleLibsDir =
            buildDir
                .map {
                    it.dir(BUNDLE_LIBS)
                }.apply {
                    get().asFile.mkdirs()
                }
        fileSystemOperations.sync {
            from(inputJarDeps)
            into(bundleLibsDir)
        }
        JpackageTool(execOperations).appImage(
            binDir = Path.of(jdkBinDir.get()),
            platform = platform,
            settings = settings,
            appLibDir = bundleLibsDir.get().asFile.toPath(),
            runtimeImageDir = inputRuntimeImageDir.get().asFile.toPath(),
            dest = outputBundleDir.get().asFile.toPath(),
        )
    }
}
