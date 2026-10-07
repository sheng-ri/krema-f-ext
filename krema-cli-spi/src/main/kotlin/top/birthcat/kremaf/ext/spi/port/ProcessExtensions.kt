/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.port

import java.util.concurrent.TimeUnit

/**
 * 等子进程退出。
 *
 * @param timeoutMillis 最长等待时间
 * @return 退出码；超时返回 null，且进程已被强制结束
 */
fun Process.waitForOrKill(timeoutMillis: Long): Int? {
    if (waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) return exitValue()
    destroy()
    if (waitFor(KILL_GRACE_MILLIS, TimeUnit.MILLISECONDS)) return exitValue()
    destroyForcibly()
    return null
}

private const val KILL_GRACE_MILLIS = 2_000L
