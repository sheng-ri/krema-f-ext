/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask
import top.birthcat.kremaf.ext.spi.iml.delegate.PlatformDetector

/** 生成可分发的平台安装包，打的就是当前平台。 */
@DisableCachingByDefault(because = "jpackage 产物带平台签名与时间戳，不可跨机器复用")
abstract class KremaBundleTask : KremaProjectTask() {
    /**
     * 目标平台标识，形如 `linux-x86_64`。
     *
     * 默认当前平台，没有「配置目标平台」这回事 —— 交叉打包不存在，`.exe` / `.deb` 只能在各自机器上打。
     * 留成 `@Internal`：只为测试能钉住平台（门禁机器不一定有 jpackage），不进对外输入契约。
     */
    @get:Internal
    abstract val platform: Property<String>

    @TaskAction
    fun bundle() {
        val ports = ports()
        // classpath 回填进 BundleSettings：jpackage 的 `--input` 吃的就是它，bundler 自己摊平，
        // 编排层不必记顺序；先建 project 再补 classpath，只为让 loadConfig 少跑一遍。
        val project = kremaProject()
        val classpath = ports.classpath(project)
        val bundler = ports.bundleFactory().create(platform.getOrElse(PlatformDetector.currentTarget()))
        val bundleDir = bundler.createProductionBundle(project.bundle.copy(classpath = classpath))
        // jpackage 的 runtime 镜像自带 jar，再摊一遍只会往安装包旁边倒垃圾。
        if (bundler.deploysClasspath) {
            ports.deployer().deploy(project, bundleDir, classpath)
        }
    }
}
