/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.config.BundleIcons
import top.birthcat.kremaf.ext.gradle.config.BundleSettings
import top.birthcat.kremaf.ext.gradle.config.resolveBundleIcon
import top.birthcat.kremaf.ext.gradle.domain.BundleFormat
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.domain.appImageDirName
import top.birthcat.kremaf.ext.gradle.tool.AppImageTool
import top.birthcat.kremaf.ext.gradle.tool.JpackageTool
import top.birthcat.kremaf.ext.gradle.tool.capture
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
abstract class KremaPlatformTask : KremaProjectTask() {
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

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val winIcon: RegularFileProperty

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
        val settings = bundleSettings()
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

    /** jpackage 安装器：复用 app-image，不再重打。 */
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

    /** AppImage：把 app-image 目录交给 `appimagetool` 封装。 */
    @Throws(GradleException::class)
    private fun appImageTool(settings: BundleSettings) {
        val rawTool =
            AppImageTool.findInPath()
                ?: if (appImageToolAutoDownload.get()) {
                    AppImageTool.download(outputDir.get().asFile.toPath())
                } else {
                    throw GradleException(
                        "PATH 上找不到 appimagetool，且 krema.bundling.appImageToolAutoDownload 为 false。" +
                            "手动安装 appimagetool 并放进 PATH，或把该开关打开。",
                    )
                }
        // appimagetool 要 app dir 里有 `<appName>.desktop`（jpackage 的 app-image 不带）。但那是本 task 的输入
        // （kremaBundle 的产物），不能由本 task 改写，否则二次运行输入哈希变了、UP-TO-DATE 失效。复制到 staging 再写元信息。
        val staging =
            outputDir
                .get()
                .asFile
                .toPath()
                .resolve("appimage-staging")
        staging.toFile().deleteRecursively()
        Files.createDirectories(staging)
        Files.walk(appImageDir.get().asFile.toPath()).use { stream ->
            for (from in stream) {
                if (from == appImageDir.get().asFile.toPath()) continue
                val to =
                    staging.resolve(
                        appImageDir
                            .get()
                            .asFile
                            .toPath()
                            .relativize(from),
                    )
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to)
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
        prepareAppImageMeta(settings, staging)
        // 下载到的是 appimagetool 自带的 AppImage，直接跑要 FUSE；多数容器 / CI 没有 FUSE。
        // 用 `--appimage-extract` 抽出内部真二进制（这一步不挂载、不需要 FUSE），后续打包走它。
        val tool = resolveAppImageTool(rawTool)
        val out =
            outputDir
                .get()
                .asFile
                .toPath()
                .resolve("${settings.appName}.AppImage")
        runCommand(
            listOf(tool.toAbsolutePath().toString(), staging.toString(), out.toAbsolutePath().toString()),
            "appimagetool 封装 AppImage 失败",
        )
        // 舞台是输出区里的临时副本，打包完即删，别把整份 app-image 留在产物里。
        staging.toFile().deleteRecursively()
    }

    /** 为 staging 里的 app-image 补上 AppImage 打包要的 `.desktop` 与根级图标（jpackage 的 app-image 都不带）。 */
    private fun prepareAppImageMeta(
        settings: BundleSettings,
        appDir: Path,
    ) {
        val desktop = appDir.resolve("${settings.appName}.desktop")
        Files.writeString(
            desktop,
            """
            [Desktop Entry]
            Type=Application
            Name=${settings.appName}
            Exec=bin/${settings.appName}
            Icon=${settings.appName}
            Terminal=false
            Categories=Utility;
            """.trimIndent(),
        )
        val iconPng = appDir.resolve("${settings.appName}.png")
        val from = settings.icon ?: appDir.resolve("lib/${settings.appName}.png")
        if (Files.exists(from)) Files.copy(from, iconPng, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * 解析出真正能用的 `appimagetool` 可执行文件。
     *
     * 下载到的工具本身是 AppImage，运行需要 FUSE；而 `--appimage-extract` 只解包内嵌的 squashfs、不挂载，
     * 因此无 FUSE 也能跑，抽出的是普通 ELF 二进制，用它打包不再依赖 FUSE。已经是真二进制（PATH 里装的那种）
     * 不认 `--appimage-extract`，抽出失败就回退到原工具。
     */
    @Throws(GradleException::class)
    private fun resolveAppImageTool(rawTool: Path): Path {
        val extractDir =
            outputDir
                .get()
                .asFile
                .toPath()
                .resolveSibling(".appimagetool-extract")
        val inner = extractDir.resolve("squashfs-root/usr/bin/appimagetool")
        if (Files.isRegularFile(inner)) return inner
        Files.createDirectories(extractDir)
        // --appimage-extract 在工作目录内把 AppImage 解包成普通 ELF 二进制，不挂载、不需 FUSE。
        val result =
            execOperations.capture(
                listOf(rawTool.toAbsolutePath().toString(), "--appimage-extract"),
                workingDir = extractDir,
            )
        if (result.exitValue != 0) return rawTool
        if (!Files.isRegularFile(inner)) {
            throw GradleException("抽出 appimagetool 失败：\n${result.output}")
        }
        return inner
    }

    @Throws(GradleException::class)
    private fun runCommand(
        command: List<String>,
        failure: String,
    ) {
        val result = execOperations.capture(command)
        if (result.exitValue != 0) throw GradleException("$failure：\n${result.output}")
    }
}
