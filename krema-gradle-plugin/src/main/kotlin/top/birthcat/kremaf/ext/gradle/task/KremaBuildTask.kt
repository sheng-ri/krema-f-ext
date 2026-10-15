/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import top.birthcat.kremaf.ext.gradle.config.BundleSettings
import top.birthcat.kremaf.ext.gradle.config.appJarName
import top.birthcat.kremaf.ext.gradle.config.assetsJarName
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * 整个应用的聚合层：把 kremaAppJar、kremaAssetsJar 与运行期依赖 jar 收进同一个 `lib/`。
 *
 * bundle task 直接拿这个目录去 jpackage。前端是「整个应用」的资源，故与后端一起在此聚合，
 * 而不是塞进 bundle 的打包逻辑里——bundle 只管最终平台分发。
 */
@CacheableTask
abstract class KremaBuildTask : KremaProjectTask() {
    /** 专用 app jar（由 kremaAppJar 产出）：classes + resources 收全，manifest 带 Main-Class。 */
    @get:InputFile
    abstract val appJar: RegularFileProperty

    /** 运行期依赖 jar（已剔除工程自身 jar），原样进 lib/。 */
    @get:Classpath
    abstract val dependencyJars: ConfigurableFileCollection

    /** 前端 dist 打的 assets jar（由 kremaAssetsJar 产出），内容落在 `assets/` 下、Krema 从 classpath 读。 */
    @get:InputFile
    abstract val assetsJar: RegularFileProperty

    @TaskAction
    fun build() {
        val settings = bundleSettings()
        val libDir =
            outputDir
                .get()
                .asFile
                .toPath()
                .resolve(LIB_DIR)
        libDir.toFile().deleteRecursively()
        Files.createDirectories(libDir)
        copyAppJar(libDir, settings)
        copyAssetsJar(libDir, settings)
    }

    private fun copyAppJar(
        libDir: Path,
        settings: BundleSettings,
    ) {
        // app jar 由 kremaAppJar 以标准 Jar task 产出，这里只落进 lib/、对齐约定命名。
        Files.copy(appJar.get().asFile.toPath(), libDir.resolve(appJarName(settings)), REPLACE_EXISTING)
        dependencyJars.files.filter { it.isFile }.forEach { jar ->
            Files.copy(jar.toPath(), libDir.resolve(jar.name), REPLACE_EXISTING)
        }
    }

    private fun copyAssetsJar(
        libDir: Path,
        settings: BundleSettings,
    ) {
        // assets jar 由 kremaAssetsJar 以标准 Jar task 产出，这里只落进 lib/、对齐约定命名。
        Files.copy(assetsJar.get().asFile.toPath(), libDir.resolve(assetsJarName(settings)), REPLACE_EXISTING)
    }

    private companion object {
        const val LIB_DIR = "lib"
    }
}
