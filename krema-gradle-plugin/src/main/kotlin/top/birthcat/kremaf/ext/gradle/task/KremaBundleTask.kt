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
 * [format] 是必填输入：组件本就没有「不选格式」这条路径，缺值即报错。标注 `@Optional`
 * 只是为了让缺值判断落在 action 里 —— 由 Gradle 属性校验先拦会吐出通用文案，
 * 指不到「去选哪个 format」。
 *
 * 与 `#32` 的 DSL 默认值（`windows→exe` / `linux→deb` / `macos→.app`）是**两个轴**：
 * 默认值管「DSL 面不填时填什么」，本 task 管「用户必须显式给」。两者并存不冲突。
 */
@DisableCachingByDefault(because = "jpackage 产物带平台签名与时间戳，不可跨机器复用")
abstract class KremaBundleTask : KremaProjectTask() {
    /** 目标平台标识，形如 `linux-x86_64`。 */
    @get:Input
    abstract val target: Property<String>

    /** 交付物格式。**无默认值**，不给就报错。 */
    @get:Input
    @get:Optional
    abstract val format: Property<String>

    @TaskAction
    fun bundle() {
        val format = resolveFormat()
        val project = kremaProject()
        val ports = ports()
        val classpath = ports.classpath(project)
        val settings = project.bundle
        val bundleDir =
            ports
                .bundleFactory()
                .create(target.get())
                .createProductionBundle(settings)
        ports.deployer().deploy(project, bundleDir, classpath)
        logger.lifecycle("kremaBundle：format=${format.name.lowercase()} target=${target.get()}")
    }

    /**
     * 显式选择格式的唯一一处。
     *
     * `#36` 落地 format 面后，命令拼装与平台子集校验会挂到这里 —— 与 `createProductionBundle`
     * 之间只留这一个接线点，免得那时再动 task 形状。本单只做「必填 + 可解析」两道闸。
     */
    private fun resolveFormat(): BundleFormat {
        val raw =
            format.orNull
                ?: error(
                    "kremaBundle 必须显式给 format —— 例如 kremaBundle { format = \"deb\" }；" +
                        "可选值：${BundleFormat.entries.joinToString("、") { it.name.lowercase() }}（见 #37 / #36）",
                )
        return BundleFormat.parse(raw)
    }
}
