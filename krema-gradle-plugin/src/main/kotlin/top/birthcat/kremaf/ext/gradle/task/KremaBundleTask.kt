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

/**
 * 生成可分发的平台安装包。
 *
 * [format] 是**必填输入，无默认值**：组件本就没有「不选格式」这条路径。标注 `@Optional` 只是
 * 让缺值判断落在 action 里 —— 由 Gradle 属性校验先拦会吐通用文案，指不到「去选哪个 format」。
 *
 * 与 DSL 面的默认值（`windows→exe` / `linux→deb` / `macos→app`）是**两个轴**：默认值管
 * 「DSL 面不填时填什么」，本 task 管「用户必须显式给」。两者并存不冲突。
 *
 * @property dev true 出开发用 bundle，false 出生产 bundle
 */
@DisableCachingByDefault(because = "打包产物带平台签名与时间戳，不可跨机器复用")
abstract class KremaBundleTask : KremaProjectTask() {
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

    @TaskAction
    fun bundle() {
        val format = resolveFormat()
        val project = kremaProject()
        val ports = ports()
        val classpath = ports.classpath(project)
        val bundleDir = ports.bundleFactory().create(target.get()).createBundle(project.bundle, dev.get())
        ports.deployer().deploy(project, bundleDir, classpath)
        logger.lifecycle("kremaBundle：format=${format.formatName} target=${target.get()} → $bundleDir")
    }

    /**
     * 显式选择格式的唯一一处：缺值即硬报错，文案指向「选哪个 format」。
     *
     * 值域由枚举拦在编译期，「给错」这条路径在 Kotlin DSL 里根本写不出来。
     */
    private fun resolveFormat(): BundleFormat =
        format.orNull
            ?: error(
                "kremaBundle 必须显式给 format —— 例如 kremaBundle { format = BundleFormat.DEB }；" +
                    "可选值：${BundleFormat.names.joinToString("、")}（见 #32 / #44）",
            )
}
