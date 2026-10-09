/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaConfigTask
import top.birthcat.kremaf.ext.gradle.KremaProcessCleanupService
import top.birthcat.kremaf.ext.gradle.PlatformBundler

/**
 * 起并阻塞前台跑 bundle 里的应用进程。
 *
 * dev 档额外先拉前端 dev server 进程；两个进程都登记到 [KremaProcessCleanupService]，
 * 用户中断（Ctrl-C）时由构建收尾统一回收，不留孤儿进程。run task 不产出文件，故不声明输出目录。
 */
@DisableCachingByDefault(because = "起长驻进程，副作用是进程而非产物")
abstract class KremaBundleRunTask : KremaConfigTask() {
    /** 目标平台标识，形如 `linux-x86_64`。 */
    @get:Input
    abstract val target: Property<String>

    /** 是否 dev 档（决定是否额外起 dev server）。 */
    @get:Input
    abstract val dev: Property<Boolean>

    /** bundle 落盘目录（与对应 bundle task 的 `outputDir` 一致）。 */
    @get:Internal
    abstract val bundleDir: DirectoryProperty

    /** 前端 dev server 命令，仅 dev 档使用。 */
    @get:Internal
    abstract val devCommand: Property<String>

    /** 进程回收服务，由注册期注入。 */
    @get:Internal
    abstract val cleanupService: Property<KremaProcessCleanupService>

    @TaskAction
    fun run() {
        val settings = bundleSettings(bundleDir.get().asFile.toPath())
        val bundler = PlatformBundler(target.get())
        val cleanup = cleanupService.get()
        val projectDir = projectDir.get().asFile

        if (dev.get()) {
            val devServer =
                ProcessBuilder(devCommand.get().split(Regex("\\s+")))
                    .directory(projectDir)
                    .inheritIO()
                    .start()
            cleanup.register(devServer)
        }

        val app = bundler.launch(bundler.bundlePath(settings, dev.get()), emptyMap())
        cleanup.register(app)
        app.waitFor()
    }
}
