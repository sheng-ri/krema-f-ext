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
import top.birthcat.kremaf.ext.gradle.KremaProcessCleanupService
import top.birthcat.kremaf.ext.gradle.config.PlatformBundler
import java.util.concurrent.TimeUnit

/**
 * 起并阻塞前台跑 bundle 里的应用进程。
 *
 * 进程登记到 [KremaProcessCleanupService]，用户中断（Ctrl-C）时由构建收尾统一回收，不留孤儿进程。
 * run task 不产出文件，故不声明输出目录。
 */
@DisableCachingByDefault(because = "起长驻进程，副作用是进程而非产物")
abstract class KremaBundleRunTask : KremaConfigTask() {
    /** 目标平台标识，形如 `linux-x86_64`。 */
    @get:Input
    abstract val target: Property<String>

    /** bundle 落盘目录（与对应 bundle task 的 `outputDir` 一致）。 */
    @get:Internal
    abstract val bundleDir: DirectoryProperty

    /** 进程回收服务，由注册期注入。 */
    @get:Internal
    abstract val cleanupService: Property<KremaProcessCleanupService>

    @TaskAction
    fun run() {
        val settings = bundleSettings(bundleDir.get().asFile.toPath())
        val bundler = PlatformBundler(target.get())
        val cleanup = cleanupService.get()

        val app = bundler.launch(settings, emptyMap())
        cleanup.register(app)
        // 轮询式等待：java 的 waitFor() 默认无视线程中断会卡死任务线程，导致构建取消时收不到收尾信号。
        // 一旦被中断（Ctrl-C）就退出循环，在 finally 里直接回收进程树，不依赖 daemon 的 close 时机。
        try {
            while (true) {
                try {
                    if (app.waitFor(500, TimeUnit.MILLISECONDS)) break
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                if (Thread.currentThread().isInterrupted()) break
            }
        } finally {
            cleanup.killAll()
        }
    }
}
