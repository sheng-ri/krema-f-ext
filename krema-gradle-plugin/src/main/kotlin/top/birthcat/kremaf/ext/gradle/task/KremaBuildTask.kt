/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask

/** 编译 Java 源码并跑一次前端生产构建。 */
@DisableCachingByDefault(because = "调用 javac 与 npm，产物含时间戳，跨机器不可复现")
abstract class KremaBuildTask : KremaProjectTask() {
    @TaskAction
    fun build() {
        val project = kremaProject()
        val ports = ports()
        val classpath = ports.classpath(project)
        ports.frontend().build(project)
        ports.javaCompiler(outputDir.get().asFile.toPath()).compile(project, classpath)
    }
}
