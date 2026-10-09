/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.task

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import top.birthcat.kremaf.ext.gradle.KremaProjectTask

/**
 * 跑 dev 流程：起 dev server 进程，再拉应用进程。
 *
 * 阻塞到用户中断、中断时把两个进程一起回收，由 #55 接上。
 */
@DisableCachingByDefault(because = "起长驻进程，副作用是进程而非产物")
abstract class KremaDevTask : KremaProjectTask() {
    @TaskAction
    fun dev() {
        val app = config().packageName
        logger.warn("kremaDev：$app 的 dev server 进程与应用进程的起停见 #55")
    }
}
