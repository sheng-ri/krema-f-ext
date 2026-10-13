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
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
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

    /** jpackage 所在的可执行文件目录，取自 JDK toolchain 而非 PATH。 */
    @get:Internal
    abstract val jdkBinDir: Property<String>

    /** 三个按平台的图标，优先级高于 `[bundle].icon`。文件不存在即跳过。 */
    @get:Internal
    abstract val macIcon: RegularFileProperty

    @get:Internal
    abstract val winIcon: RegularFileProperty

    @get:Internal
    abstract val linuxIcon: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    @Throws(GradleException::class)
    fun bundle() {
        val settings = bundleSettings()
        val icon =
            resolveBundleIcon(
                platform = Platform.of(targetPlatform()),
                macIcon = macIcon.orNull?.asFile?.toPath(),
                windowsIcon = winIcon.orNull?.asFile?.toPath(),
                linuxIcon = linuxIcon.orNull?.asFile?.toPath(),
                tomlIcon = settings.icon,
            )
        JpackageTool(execOperations).appImage(
            binDir = Path.of(jdkBinDir.get()),
            settings = settings.copy(icon = icon),
            appLibDir = appLibDir.get().asFile.toPath(),
            runtimeImageDir = runtimeImageDir.get().asFile.toPath(),
            dest = outputDir.get().asFile.toPath(),
        )
    }

    private fun targetPlatform(): String {
        // app-image 落盘平台即宿主平台；图标按它选。
        return System.getProperty("os.name")
    }
}
