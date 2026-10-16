/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.config.BundleIcons
import top.birthcat.kremaf.ext.gradle.config.BundleSettings
import top.birthcat.kremaf.ext.gradle.config.resolveBundleIcon
import top.birthcat.kremaf.ext.gradle.domain.BundleFormat
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.appImageDirName
import top.birthcat.kremaf.ext.gradle.tool.AppImageTool
import top.birthcat.kremaf.ext.gradle.tool.JpackageTool
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * 单个平台包格式的产物 task，按需注册（见 [top.birthcat.kremaf.ext.gradle.kremaPlatformTaskName]）。
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

    /** 打包输入，`krema { }` 块的覆盖值已压过 toml。 */
    @get:Internal
    abstract val bundleSettings: Property<BundleSettings>

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

    /** 宿主平台：跨平台复用无意义，故作为缓存键的一部分。 */
    @get:Input
    abstract val hostPlatform: Property<Platform>

    /** AppImage 缺工具时是否自动下载。仅 [BundleFormat.Producer.APP_IMAGE_TOOL] 用到。 */
    @get:Input
    abstract val appImageToolAutoDownload: Property<Boolean>

    /**
     * 三个按平台的图标，优先级高于 `[bundle].icon`。进缓存键：图标内容变了产物就该重打。
     *
     * 按内容而非路径判变更，且文件缺失即跳过，与 [resolveBundleIcon] 的语义一致。
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val macIcon: RegularFileProperty

    /** Windows 图标（ico）。缺失即跳过 `--icon`。 */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val winIcon: RegularFileProperty

    /** Linux 图标（png）。缺失即跳过 `--icon`。 */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val linuxIcon: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    @Throws(GradleException::class)
    fun buildPlatform() {
        val format = format.get()
        val settings = bundleSettings.get()
        when (format.producer) {
            BundleFormat.Producer.APP_IMAGE -> copyAppImage(settings)
            BundleFormat.Producer.JPACKAGE_INSTALLER -> jpackageInstaller(format, settings)
            BundleFormat.Producer.APP_IMAGE_TOOL -> appImageTool(settings)
        }
    }

    /** `APP_IMAGE`：app-image 目录本身即产物（macOS `.app`），直接搬到本格式目录。 */
    private fun copyAppImage(settings: BundleSettings) {
        val dest =
            outputDir
                .get()
                .asFile
                .toPath()
                .resolve(hostPlatform.get().appImageDirName(settings.appName))
        dest.toFile().deleteRecursively()
        Files.createDirectories(dest.parent)
        // app-image 带只读启动器，复制前先保证源可读写，否则某些平台复制会报权限错。
        val src = appImageDir.get().asFile.toPath()
        src.toFile().walkTopDown().forEach { it.setWritable(true) }
        Files.walk(src).use { stream ->
            for (from in stream) {
                if (from == src) continue
                val to = dest.resolve(src.relativize(from))
                if (Files.isDirectory(
                        from,
                    )
                ) {
                    Files.createDirectories(
                        to,
                    )
                } else {
                    Files.copy(
                        from,
                        to,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.COPY_ATTRIBUTES,
                    )
                }
            }
        }
    }

    @Throws(GradleException::class)
    private fun jpackageInstaller(
        format: BundleFormat,
        settings: BundleSettings,
    ) {
        val icon =
            resolveBundleIcon(
                platform = hostPlatform.get(),
                icons =
                    BundleIcons(
                        mac = macIcon.orNull?.asFile?.toPath(),
                        windows = winIcon.orNull?.asFile?.toPath(),
                        linux = linuxIcon.orNull?.asFile?.toPath(),
                        toml = settings.icon,
                    ),
            )
        JpackageTool(execOperations).installer(
            binDir = Path.of(jdkBinDir.get()),
            format = format,
            settings = settings,
            appImageDir = appImageDir.get().asFile.toPath(),
            dest = outputDir.get().asFile.toPath(),
            icon = icon,
        )
    }

    @Throws(GradleException::class)
    private fun appImageTool(settings: BundleSettings) {
        AppImageTool(execOperations).packageAppImage(
            appImageDir = appImageDir.get().asFile.toPath(),
            outputDir = outputDir.get().asFile.toPath(),
            settings = settings,
            autoDownload = appImageToolAutoDownload.get(),
        )
    }
}
