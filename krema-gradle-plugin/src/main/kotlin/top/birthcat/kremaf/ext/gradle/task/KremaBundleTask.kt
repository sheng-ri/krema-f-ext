/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.FileOps
import top.birthcat.kremaf.ext.gradle.KremaProjectTask
import top.birthcat.kremaf.ext.gradle.PlatformBundler

/**
 * 按目标平台生成可分发形态的 bundle，并把应用 classpath 收进去。
 *
 * dev 与 prod 共用一张 task 类，靠 [dev] 分档：dev 出最小形态（macOS 才给正经 `.app`，
 * 其余平台靠 devUrl 拉前端），且不打包 `dist`；prod 全平台出完整形态并打包 `dist`。
 * classpath 来自 Gradle 的 `runtimeClasspath`，不认任何自造的源码目录约定。
 */
@DisableCachingByDefault(because = "启动器按本机平台落盘，不做跨机器复用")
abstract class KremaBundleTask : KremaProjectTask() {
    /** 目标平台标识，形如 `linux-x86_64`。 */
    @get:Input
    abstract val target: Property<String>

    /** 是否 dev 档。 */
    @get:Input
    abstract val dev: Property<Boolean>

    /** 应用运行所需的 classpath（含主 source set 产出与运行期依赖），作为 `@Classpath` 输入参与 up-to-date。 */
    @get:Classpath
    abstract val classpathFiles: ConfigurableFileCollection

    /** prod 档要打进 bundle 的前端 `dist`；dev 档为空集合。 */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val distFiles: ConfigurableFileCollection

    @TaskAction
    fun bundle() {
        val settings = bundleSettings()
        val bundler = PlatformBundler(target.get())
        if (dev.get()) {
            bundler.createDevBundle(settings)
        } else {
            bundler.createProductionBundle(settings)
        }
        val javaDir = bundler.javaTargetDir(settings, dev.get())
        classpathFiles.files.forEach { FileOps.copyTree(it.toPath(), javaDir) }
        if (!dev.get()) {
            distFiles.files.forEach { FileOps.copyTree(it.toPath(), bundler.distTargetDir(settings)) }
        }
    }
}
