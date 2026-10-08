/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask

/**
 * 在 [KremaBuildTask] 之上打 native-image。
 *
 * 本单只落 task 骨架：native-image 的命令拼装与工具链定位留给 #26（`Blocked by: #25`）。
 */
@DisableCachingByDefault(because = "native-image 耗时且依赖本机工具链，见 #26")
abstract class KremaBuildNativeTask : KremaProjectTask() {
    @TaskAction
    fun buildNative() {
        val project = kremaProject()
        val ports = ports()
        ports.javaCompiler(outputDir.get().asFile.toPath()).compile(project, ports.classpath(project))
        logger.warn("kremaBuildNative：native-image 工具链接通见 #26，本轮只产出常规编译结果")
    }
}
