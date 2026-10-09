/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Path

/**
 * 带产物目录的 Krema task：打包与构建类 task 的共同基类。
 *
 * 配置输入在 [KremaConfigTask] 上；这里只补一个 `@OutputDirectory` 的 `outputDir`，
 * 并据此给出不传目录参数的 [bundleSettings] 便捷重载。
 */
@DisableCachingByDefault(because = "产物是按本机平台落盘的启动器与密钥，不做跨机器复用")
abstract class KremaProjectTask : KremaConfigTask() {
    /** 产物根目录。 */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** 打包输入，`krema { }` 块的覆盖值已压过 toml。 */
    protected fun bundleSettings(): BundleSettings = bundleSettings(outputDir.get().asFile.toPath())
}

/** 配置输入 + 产物目录约定（默认 `build/kerma`）。 */
internal fun KremaProjectTask.configureProject(ext: KremaExtension) {
    (this as KremaConfigTask).configureProject(ext)
    outputDir.convention(ext.outputDir.orElse(project.layout.buildDirectory.dir("krema")))
}
