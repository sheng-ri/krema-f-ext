/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle.api

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Build service that tracks long-lived processes started by run tasks and destroys their process
 * trees when the build finishes or is canceled, so no orphan application is left behind.
 */
abstract class KremaProcessCleanupService :
    BuildService<BuildServiceParameters.None>,
    AutoCloseable {
    private val processes = CopyOnWriteArrayList<Process>()

    /** Registers a process for cleanup. */
    fun register(process: Process) {
        processes.add(process)
    }

    /** Destroys every registered process tree; safe to call repeatedly. */
    fun killAll() {
        for (process in processes) {
            killTree(process)
        }
    }

    /** Destroys every registered process tree when the build ends. */
    override fun close() {
        killAll()
    }

    // 先杀子树再杀自己：应用进程可能又拉了子进程（WebView 等）。
    private fun killTree(process: Process) {
        runCatching { process.descendants().forEach { runCatching { it.destroy() } } }
        runCatching { process.destroy() }
    }
}
