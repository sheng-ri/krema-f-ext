/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.api.BundleSettings
import top.birthcat.kremaf.ext.gradle.api.KremaProcessCleanupService
import top.birthcat.kremaf.ext.gradle.api.KremaTaskNames
import top.birthcat.kremaf.ext.gradle.internal.config.PROCESS_CLEANUP_SERVICE
import top.birthcat.kremaf.ext.gradle.internal.tool.PlatformBundleLauncher
import java.util.concurrent.TimeUnit

/**
 * Runs the application from the packaged app-image and blocks until it exits.
 *
 * The process is registered with [KremaProcessCleanupService], so cancelling the build destroys
 * the whole process tree instead of leaving an orphan behind.
 */
@DisableCachingByDefault(because = "starts a long-lived process; the side effect is a process, not an artifact")
abstract class KremaBundleRunTask : DefaultTask() {
    init {
        group = KremaTaskNames.GROUP
        description = "Run the application (long-lived, not part of CI gating)"
    }

    /** Platform identifier, e.g. linux-x86_64. */
    @get:Input
    abstract val target: Property<String>

    /** Bundle root; the app-image is bundleDir/appName. */
    @get:Internal
    abstract val bundleDir: DirectoryProperty

    /** Naming inputs; values from the extension already override krema.toml. */
    @get:Internal
    abstract val bundleSettings: Property<BundleSettings>

    /** Cleanup service injected by Gradle from the shared build service. */
    @get:ServiceReference(PROCESS_CLEANUP_SERVICE)
    abstract val cleanupServiceProp: Property<KremaProcessCleanupService>

    /** Starts the launcher and waits for it, polling so cancellation is observable. */
    @Throws(GradleException::class)
    @TaskAction
    fun run() {
        val settings = bundleSettings.get()
        val appImageDir =
            bundleDir
                .get()
                .asFile
                .toPath()
                .resolve(settings.appName)
        val bundler = PlatformBundleLauncher(appImageDir, target.get())

        val app = bundler.launch(settings, emptyMap())
        val cleanupService = cleanupServiceProp.get()
        cleanupService.register(app)
        // 轮询等待：waitFor() 不响应中断，取消构建时收不到收尾信号。
        var interrupted = false
        try {
            while (true) {
                try {
                    if (app.waitFor(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)) break
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    interrupted = true
                    break
                }
                if (Thread.currentThread().isInterrupted) {
                    interrupted = true
                    break
                }
            }
        } finally {
            cleanupService.killAll()
        }
        // 非中断退出时，非零退出码即启动失败，不能把崩溃当成成功。
        if (!interrupted) {
            val code = app.exitValue()
            if (code != 0) throw GradleException("Application process exited with code $code; see the launcher output above")
        }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 500L
    }
}
