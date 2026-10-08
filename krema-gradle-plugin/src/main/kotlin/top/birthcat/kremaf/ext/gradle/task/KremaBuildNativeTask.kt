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
 * 工具链定位、命令拼装与收尾都在 `JavaCompiler.compileNative` 里，这张 task 只剩「把 port 接上」。
 */
@DisableCachingByDefault(because = "native-image 耗时且依赖本机 GraalVM，产物不可跨机器复用")
abstract class KremaBuildNativeTask : KremaProjectTask() {
    @TaskAction
    fun buildNative() {
        val project = kremaProject()
        val ports = ports()
        val binary = ports.javaCompiler(outputDir.get().asFile.toPath()).compileNative(project, ports.classpath(project))
        logger.lifecycle("kremaBuildNative：native 可执行文件落在 $binary")
    }
}
