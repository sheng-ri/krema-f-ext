/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.CopyOnWriteArrayList

/** 登记 run task 拉起的长驻进程，构建收尾（或中断）时杀掉整棵进程树，避免孤儿进程。 */
abstract class KremaProcessCleanupService :
    BuildService<BuildServiceParameters.None>,
    AutoCloseable {
    private val processes = CopyOnWriteArrayList<Process>()

    fun register(process: Process) {
        processes.add(process)
    }

    /** 幂等，可重复调用。 */
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
