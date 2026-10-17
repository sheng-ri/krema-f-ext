/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.api.BundleFormat
import top.birthcat.kremaf.ext.gradle.api.BundleSettings
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.api.WindowsInstallerOptions
import top.birthcat.kremaf.ext.gradle.internal.tool.AppImageTool
import top.birthcat.kremaf.ext.gradle.internal.tool.JpackageTool
import java.nio.file.Path
import javax.inject.Inject

/**
 * Builds one platform package format from the app-image produced by kremaBundle.
 *
 * A task is registered for each format configured for the host platform; the artifact is written
 * to build/krema/platform/ followed by the format id. jpackage installers and appimagetool reuse the existing
 * app-image instead of re-running jpackage from scratch.
 */
@CacheableTask
abstract class KremaPlatformTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
    }

    /** Directory the platform package is written to. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Naming inputs; values from the extension already override krema.toml. */
    @get:Nested
    abstract val bundleSettings: Property<BundleSettings>

    /** Format this task produces, taken from the matching property in krema.bundling. */
    @get:Input
    abstract val format: Property<BundleFormat>

    /**
     * Windows installer flags from the bundle.windows table; unset on every other format.
     *
     * jpackage rejects the `--win-*` family outside Windows installers, so the property stays
     * empty unless this task produces an exe or msi — hence optional: a missing value is the input,
     * not a configuration mistake.
     */
    @get:Nested
    @get:Optional
    abstract val windowsOptions: Property<WindowsInstallerOptions>

    /** app-image produced by kremaBundle, used as the input for this format; compared by content. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appImageDir: DirectoryProperty

    /** Directory of the jpackage executable from the JDK toolchain. */
    @get:Input
    abstract val jdkBinDir: Property<String>

    /** Whether to download appimagetool when missing; only used by [BundleFormat.Producer.APP_IMAGE_TOOL]. */
    @get:Input
    abstract val appImageToolAutoDownload: Property<Boolean>

    /** Injected exec operations. */
    @get:Inject
    abstract val execOperations: ExecOperations

    /** Injected file system operations. */
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    /** Produces the package with the tool selected by [BundleFormat.producer]. */
    @Throws(GradleException::class)
    @TaskAction
    fun buildPlatform() {
        val format = format.get()
        when (format.producer) {
            BundleFormat.Producer.APP_IMAGE -> copyAppImage()
            BundleFormat.Producer.JPACKAGE_INSTALLER -> jpackageInstaller(format)
            BundleFormat.Producer.APP_IMAGE_TOOL -> appImageTool()
        }
    }

    /** app-image 目录本身就是产物，直接搬到本格式目录。 */
    private fun copyAppImage() {
        fileSystemOperations.sync {
            from(appImageDir)
            into(outputDir)
        }
    }

    private fun jpackageInstaller(format: BundleFormat) {
        JpackageTool(execOperations).installer(
            binDir = Path.of(jdkBinDir.get()),
            format = format,
            settings = bundleSettings.get(),
            windows = windowsOptions.getOrElse(WindowsInstallerOptions()),
            appImageDir = appImageDir.get().asFile.toPath(),
            dest = outputDir.get().asFile.toPath(),
        )
    }

    private fun appImageTool() {
        AppImageTool(execOperations).packageAppImage(
            appImageDir = appImageDir.get().asFile.toPath(),
            outputDir = outputDir.get().asFile.toPath(),
            settings = bundleSettings.get(),
            autoDownload = appImageToolAutoDownload.get(),
        )
    }
}
