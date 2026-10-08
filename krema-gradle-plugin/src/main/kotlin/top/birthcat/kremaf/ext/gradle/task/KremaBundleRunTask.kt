/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask
import top.birthcat.kremaf.ext.spi.port.BundleFormat
import top.birthcat.kremaf.ext.spi.port.LaunchSettings
import top.birthcat.kremaf.ext.spi.port.requireRunnable

/**
 * 打好 bundle 之后把它跑起来。
 *
 * 与 [KremaDevTask] 故意不同：这里**阻塞 `waitFor()`**，Gradle 会话活到窗口关；`kremaDev`
 * 起完即返回。两处都写在 README 里，免得被当成不一致顺手改齐。
 *
 * [handshake] 开时交给中间层判握手标志行 —— 终端人看的输出与 CI 判的那行共用一个闸门。
 *
 * @property dev true 跑 dev bundle（顺带拉起前端 dev server），false 跑生产 bundle
 */
@DisableCachingByDefault(because = "起长驻应用进程，副作用是进程而非产物")
abstract class KremaBundleRunTask : KremaProjectTask() {
    /** 目标平台标识，形如 `linux-x86_64`。 */
    @get:Input
    abstract val target: Property<String>

    /** 交付物格式。**无默认值**，不给就报错。 */
    @get:Input
    @get:Optional
    abstract val format: Property<BundleFormat>

    /** 打包编年：dev / prod。 */
    @get:Input
    abstract val dev: Property<Boolean>

    /** true 时捕获 stdout 判握手标志行，CI / 验证用；默认 `inheritIO()` 给人看。 */
    @get:Input
    abstract val handshake: Property<Boolean>

    @TaskAction
    fun run() {
        val format = resolveFormat()
        val project = kremaProject()
        val ports = ports()

        // 可跑守门放在起进程之前：安装器格式的产物没有可 exec 的入口，静默跳过等于骗用户。
        val bundleDir = ports.bundleFactory().create(target.get()).createBundle(project.bundle, dev.get())
        requireRunnable(format, bundleDir)

        // dev 跑之前把前端 dev server 拉起来；server 已在监听就复用，不重复起。
        if (dev.get() && !ports.frontend().isDevServerUp(project.frontend.devUrl)) {
            ports.frontend().startDevServer(project, ports.env(project))
        }

        val classpath = ports.classpath(project)
        logger.lifecycle("launching $bundleDir with mainClass=${project.java.mainClass} (format=${format.formatName})")

        val process =
            ports
                .bundleFactory()
                .create(target.get())
                .run(
                    LaunchSettings(
                        bundlePath = bundleDir,
                        classpath = classpath.entries,
                        mainClass = project.java.mainClass ?: project.bundle.mainClass,
                        devUrl = project.frontend.devUrl.takeIf { dev.get() },
                        libPath = classpath.nativeLibDir,
                        handshake = handshake.get(),
                    ),
                )
        process.waitFor()
    }

    private fun resolveFormat(): BundleFormat =
        format.orNull
            ?: error(
                "bundle run 类 task 必须显式给 format —— 例如 kremaBundleProdRun { format = BundleFormat.APP_IMAGE }；" +
                    "可选值：${BundleFormat.names.joinToString("、")}（见 #32 / #44）",
            )
}
