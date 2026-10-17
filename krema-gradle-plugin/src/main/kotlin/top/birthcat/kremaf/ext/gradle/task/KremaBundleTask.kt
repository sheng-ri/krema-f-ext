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
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import top.birthcat.kremaf.ext.gradle.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.config.BUNDLE_DEPS
import top.birthcat.kremaf.ext.gradle.domain.BundleSettings
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
abstract class KremaBundleTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description = "Build a runnable, uncompressed app-image (build/krema/bundle) with the jlink-trimmed runtime"
    }

    @get:Internal
    abstract val buildDir: DirectoryProperty

    /** 产物根目录。 */
    @get:OutputDirectory
    abstract val outputBundleDir: DirectoryProperty

    /** 打包输入，`krema { }` 块的覆盖值已压过 toml。 */
    @get:Nested
    abstract val bundleSettings: Property<BundleSettings>

    /**
     * jlink 裁出的运行时镜像，即 jpackage 的 `--runtime-image`。
     *
     * 同 [appLibDir]：jlink 的产物路径取决于 `outputDir`，内容才是唯一身份。
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputRuntimeImageDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJarDeps: ConfigurableFileCollection

    /** jpackage 所在的可执行文件目录，取自 JDK toolchain 而非 PATH。进缓存键：换 JDK 即视为新产物。 */
    @get:Input
    abstract val jdkBinDir: Property<String>

    /** 宿主平台：跨平台复用无意义，故作为缓存键的一部分。 */
    @get:Input
    abstract val hostPlatform: Property<Platform>

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun bundle() {
        val platform = hostPlatform.get()
        val settings = bundleSettings.get()
        val bundleLibDir =
            buildDir
                .map {
                    it.dir(BUNDLE_DEPS)
                }.apply {
                    get().asFile.mkdirs()
                }
        fileSystemOperations.sync {
            from(inputJarDeps)
            into(bundleLibDir)
        }
        JpackageTool(execOperations).appImage(
            binDir = Path.of(jdkBinDir.get()),
            platform = platform,
            settings = settings,
            appLibDir = bundleLibDir.get().asFile.toPath(),
            runtimeImageDir = inputRuntimeImageDir.get().asFile.toPath(),
            dest = outputBundleDir.get().asFile.toPath(),
        )
    }
}
