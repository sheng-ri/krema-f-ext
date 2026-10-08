/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask

/**
 * 跑 dev 流程：起前端 dev server，再拉窗口。
 *
 * dev server 是长驻进程，本 task 起完即返回，不阻塞 Gradle 会话 —— 生命周期口径见地图
 * #11 的「Not yet specified」，本轮不替它下结论。
 */
@DisableCachingByDefault(because = "起长驻 dev server，副作用是进程而非产物")
abstract class KremaDevTask : KremaProjectTask() {
    @TaskAction
    fun dev() {
        val project = kremaProject()
        val ports = ports()
        ports.frontend().startDevServer(project)
        ports.javaCompiler(outputDir.get().asFile.toPath()).compile(project, ports.classpath(project))
    }
}
