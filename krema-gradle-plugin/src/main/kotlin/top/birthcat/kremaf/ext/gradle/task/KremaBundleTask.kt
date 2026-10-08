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
    }
}
