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
import top.birthcat.kremaf.ext.gradle.domain.Platform
import top.birthcat.kremaf.ext.gradle.tool.JpackageTool
import java.nio.file.Path
import javax.inject.Inject

/**
 * 用 jpackage 产出未压缩、可直接运行的 app-image 目录（`build/krema/bundle`）。
 *
 * 这是所有平台包的共同底座：平台格式 task 复用这份 app-image，不再从 `--input lib` 重打一遍。
 * 前端资源与后端聚合都在 `kremaBuild` 完成，这里只做平台打包，不碰任何资源。
 *
 * 可缓存：同一台机器、同一个 JDK、同一个宿主平台下产物不变就直接复用缓存，不必重打。
 * JDK 路径进缓存键，换 JDK 即视为新产物；宿主平台也单独作为输入，避免把一个平台的包当另一个平台用。
 */
@CacheableTask
abstract class KremaBundleTask : KremaProjectTask() {
    /**
     * `kremaBuild` 聚好的 `lib/` 目录：app jar + 依赖 jar + assets jar。
     *
     * 按内容而非路径判变更：jar 内容才是打包的身份，目录随 `outputDir` 挪位不该让整个 app-image 重打。
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appLibDir: DirectoryProperty

    /**
     * jlink 裁出的运行时镜像，即 jpackage 的 `--runtime-image`。
     *
     * 同 [appLibDir]：jlink 的产物路径取决于 `outputDir`，内容才是唯一身份。
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeImageDir: DirectoryProperty

    /** jpackage 所在的可执行文件目录，取自 JDK toolchain 而非 PATH。进缓存键：换 JDK 即视为新产物。 */
    @get:Input
    abstract val jdkBinDir: Property<String>

    /** 宿主平台：跨平台复用无意义，故作为缓存键的一部分。 */
    @get:Input
    abstract val hostPlatform: Property<Platform>

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
    fun bundle() {
        val platform = hostPlatform.get()
        val settings = bundleSettings()
        val icon =
            resolveBundleIcon(
                platform = platform,
                icons =
                    BundleIcons(
                        mac = macIcon.orNull?.asFile?.toPath(),
                        windows = winIcon.orNull?.asFile?.toPath(),
                        linux = linuxIcon.orNull?.asFile?.toPath(),
                        toml = settings.icon,
                    ),
            )
        JpackageTool(execOperations).appImage(
            binDir = Path.of(jdkBinDir.get()),
            platform = platform,
            settings = settings.copy(icon = icon),
            appLibDir = appLibDir.get().asFile.toPath(),
            runtimeImageDir = runtimeImageDir.get().asFile.toPath(),
            dest = outputDir.get().asFile.toPath(),
        )
    }
}
