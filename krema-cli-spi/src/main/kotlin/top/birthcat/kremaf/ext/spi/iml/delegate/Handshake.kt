/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import top.birthcat.kremaf.ext.spi.port.LaunchSettings
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 应用起来了的标志行。
 *
 * 判的就是它：人看的终端与 CI 看的握手共用一个总闸门，见 [processFor]。
 */
const val HANDSHAKE_LINE: String = "KREMA_APP_STARTED"

/** 握手等待上限，超了就当没起来 —— 比无限挂住强。 */
private const val HANDSHAKE_TIMEOUT_SECONDS: Long = 120

/** 轮询间隔：既盯标志行也盯进程退出，不靠一次长等待。 */
private const val POLL_MILLIS: Long = 200

/**
 * 起一个应用进程。
 *
 * `inheritIO()` 与自带 `Pipe` 互斥，所以这一个函数就是两种模式的唯一分叉点：
 *
 * - [LaunchSettings.handshake] 关（默认）：`inheritIO()`，终端里直接是 app 输出，日常开发用。
 * - 开：接管 stdout，边转发到本进程终端边找 [HANDSHAKE_LINE]，找到即返回 —— **输出不丢**，CI 用。
 *
 * 环境变量从 `ProcessBuilder.environment()` 塞，不进 port 签名。
 *
 * @param launcher 启动命令与参数
 * @param settings 启动输入
 * @return 已启动的进程句柄；握手模式下返回时标志行已经出现
 * @throws IllegalStateException 握手模式下进程先于标志行退出，或超时
 */
internal fun processFor(
    launcher: List<String>,
    settings: LaunchSettings,
): Process {
    val builder = ProcessBuilder(launcher).applyEnvironment(settings)
    if (!settings.handshake) {
        return builder.inheritIO().start()
    }
    val process = builder.redirectErrorStream(true).start()
    awaitHandshake(process)
    return process
}

/** 启动时追加给应用的环境变量，值全取自 [LaunchSettings]。 */
private fun ProcessBuilder.applyEnvironment(settings: LaunchSettings): ProcessBuilder {
    settings.devUrl?.let { environment()["KREMA_DEV_URL"] = it }
    settings.libPath?.let { environment()["KREMA_NATIVE_LIB_DIR"] = it.toString() }
    return this
}

/**
 * 盯着 stdout：转发每一行、等标志行。
 *
 * 转发放在独立线程 —— 主线程要能等标志行，不能让「读输出」把它卡死。
 *
 * 进程先退就当场判负，不等满超时：窗口没起来时干等两分钟毫无意义，且把「起不来」
 * 拖成「疑似卡死」。
 */
private fun awaitHandshake(process: Process) {
    val seen = CountDownLatch(1)
    val pump =
        Thread {
            BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    println(line)
                    if (line.contains(HANDSHAKE_LINE)) seen.countDown()
                }
            }
        }
    pump.isDaemon = true
    pump.start()
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(HANDSHAKE_TIMEOUT_SECONDS)
    while (seen.count > 0L) {
        if (seen.await(POLL_MILLIS, TimeUnit.MILLISECONDS)) return
        if (!process.isAlive) {
            error("应用没打出握手行 '$HANDSHAKE_LINE' 就退出了（exit=${process.exitValue()}）")
        }
        if (System.nanoTime() > deadline) {
            process.destroyForcibly()
            error("应用在 ${HANDSHAKE_TIMEOUT_SECONDS}s 内没打出握手行 '$HANDSHAKE_LINE'")
        }
    }
}
