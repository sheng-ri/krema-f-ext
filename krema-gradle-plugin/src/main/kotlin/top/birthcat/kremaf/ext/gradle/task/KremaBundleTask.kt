/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask

/** 生成可分发的平台安装包。 */
@DisableCachingByDefault(because = "jpackage 产物带平台签名与时间戳，不可跨机器复用")
abstract class KremaBundleTask : KremaProjectTask() {
    /** 目标平台标识，形如 `linux-x86_64`。 */
    @get:Input
    abstract val target: Property<String>

    @TaskAction
    fun bundle() {
        val ports = ports()
        // classpath 先解析，再进 BundleSettings：jpackage 的 `--input` 吃的就是它，
        // bundler 自己摊平，编排层不必记顺序。
        val classpath = ports.classpath(kremaProject())
        val project = kremaProject(classpath)
        val bundler = ports.bundleFactory().create(target.get())
        val bundleDir = bundler.createProductionBundle(project.bundle)
        // jpackage 的 runtime 镜像自带 jar，再摊一遍只会往安装包旁边倒垃圾。
        if (bundler.deploysClasspath) {
            ports.deployer().deploy(project, bundleDir, classpath)
        }
    }
}
