/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask
import java.time.Duration

/**
 * 在 [KremaBuildTask] 之上打 native-image。
 *
 * 工具链定位、命令拼装与收尾都在 `JavaCompiler.compileNative` 里，这张 task 只剩「把 port 接上」。
 *
 * native-image 是这张 task 里唯一的长跑动作，所以要有个超时兜住卡死。Gradle 的 `ExecSpec` 没有超时
 * 参数，能用的只有本 task 自己的 `timeout` —— 它超时后中断 task 线程，Gradle 随之收掉本 task 起的
 * 子进程（`GradleCommandRunner` 特意走 `ExecOperations` 就是为了接住这条生命周期）。
 * 默认给 30 分钟：native-image 本身就要跑几分钟，这个数只用来掐真卡住的构建，不掐慢构建；
 * 真要打大工程，在自己的 build script 里覆写 `tasks.kremaBuildNative { timeout = ... }` 即可。
 */
@DisableCachingByDefault(because = "native-image 耗时且依赖本机 GraalVM，产物不可跨机器复用")
abstract class KremaBuildNativeTask : KremaProjectTask() {
    init {
        timeout.convention(NATIVE_IMAGE_TIMEOUT)
    }

    @TaskAction
    fun buildNative() {
        val project = kremaProject()
        val ports = ports()
        val binary = ports.javaCompiler(outputDir.get().asFile.toPath()).compileNative(project, ports.classpath(project))
        logger.lifecycle("kremaBuildNative：native 可执行文件落在 $binary")
    }

    private companion object {
        private val NATIVE_IMAGE_TIMEOUT: Duration = Duration.ofMinutes(30)
    }
}
