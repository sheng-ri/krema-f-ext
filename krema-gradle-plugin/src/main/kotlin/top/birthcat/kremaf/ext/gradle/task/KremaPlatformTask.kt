/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.domain.BundleFormat
import top.birthcat.kremaf.ext.gradle.domain.BundleSettings
import top.birthcat.kremaf.ext.gradle.tool.AppImageTool
import top.birthcat.kremaf.ext.gradle.tool.JpackageTool
import java.nio.file.Path
import javax.inject.Inject

/**
 * 单个平台包格式的产物 task，按需注册（见 [top.birthcat.kremaf.ext.gradle.domain.kremaPlatformTaskName]）。
 *
 * 复用 `kremaBundle` 已经打好的 app-image，按 [BundleFormat.producer] 选工具造出本格式的可安装件，
 * 落进 `build/krema/platform/<format id>`：jpackage 安装器走 `--app-image` 复用，AppImage 走外部
 * `appimagetool`。不再从 `--input lib` 把 jpackage 重跑一遍。
 *
 * 可缓存：同一台机器、同一个 JDK、同一个宿主平台下产物不变就直接复用缓存，不必重打。
 * JDK 路径进缓存键，换 JDK 即视为新产物；宿主平台也单独作为输入，避免把 Linux 的 deb 当 macOS 的 dmg 用。
 */
@CacheableTask
abstract class KremaPlatformTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
    }

    /** 产物根目录。 */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Nested
    abstract val bundleSettings: Property<BundleSettings>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJars: ConfigurableFileCollection

    /** 本 task 要出的平台包格式，取自 `krema.bundling` 对应平台的格式集合。 */
    @get:Input
    abstract val format: Property<BundleFormat>

    /**
     * `kremaBundle` 产出的 app-image 目录，作为本格式产物的输入。
     *
     * 按内容而非路径判变更：app-image 由 `kremaBundle` 的 `outputDir` 定位，目录挪位不代表产物变了。
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appImageDir: DirectoryProperty

    /** jpackage 所在的可执行文件目录，取自 JDK toolchain 而非 PATH。进缓存键：换 JDK 即视为新产物。 */
    @get:Input
    abstract val jdkBinDir: Property<String>

    /** AppImage 缺工具时是否自动下载。仅 [BundleFormat.Producer.APP_IMAGE_TOOL] 用到。 */
    @get:Input
    abstract val appImageToolAutoDownload: Property<Boolean>

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun buildPlatform() {
        val format = format.get()
        when (format.producer) {
            BundleFormat.Producer.APP_IMAGE -> copyAppImage()
            BundleFormat.Producer.JPACKAGE_INSTALLER -> jpackageInstaller(format)
            BundleFormat.Producer.APP_IMAGE_TOOL -> appImageTool()
        }
    }

    /** `APP_IMAGE`：app-image 目录本身即产物（macOS `.app`），直接搬到本格式目录。 */
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
