/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 跟踪 run task 拉起的长驻进程，构建结束时（含用户中断）一并回收，避免留下孤儿进程。
 *
 * `BuildService` 在构建收尾时关闭，Ctrl-C 取消也会触发，故把进程登记在这里、在 [close] 里杀掉整棵进程树。
 * 但 TestKit 下 daemon 取消不一定走到 [close]，所以 run task 在被中断时也会直接调用 [killAll] 兜底。
 */
abstract class KremaProcessCleanupService :
    BuildService<BuildServiceParameters.None>,
    AutoCloseable {
    private val processes = CopyOnWriteArrayList<Process>()

    fun register(process: Process) {
        processes.add(process)
    }

    /** 杀掉所有登记进程的整棵进程树。幂等，可重复调用。 */
    fun killAll() {
        for (process in processes) {
            killTree(process)
        }
    }

    override fun close() {
        killAll()
    }

    private fun killTree(process: Process) {
        // 先杀子树再杀自己：应用进程可能又拉了子进程（WebView 等）。
        runCatching { process.descendants().forEach { runCatching { it.destroy() } } }
        runCatching { process.destroy() }
    }
}
