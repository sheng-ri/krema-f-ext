/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.spi.iml.delegate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import top.birthcat.kremaf.ext.spi.port.LaunchSettings
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path

/**
 * 启动总闸门的两态：默认 `inheritIO()` 给人看，开时捕获 stdout 判握手。
 *
 * 用 JDK 自带的单类程序做被测进程，绕开 bundle 与 classpath，只验开关本身。
 */
class HandshakeTest {
    private fun settings(handshake: Boolean) =
        LaunchSettings(
            bundlePath = Path.of("."),
            classpath = emptyList(),
            mainClass = "ignored",
            handshake = handshake,
        )

    @Test
    fun handshakeOnWaitsForMarkerLine() {
        assumeTrue(PlatformDetector.detect() != Platform.WINDOWS, "用 bash -c 做被测进程")

        val out = ByteArrayOutputStream()
        val original = System.out
        val process =
            try {
                System.setOut(PrintStream(out, true, Charsets.UTF_8))
                processFor(listOf("bash", "-c", "echo $HANDSHAKE_LINE"), settings(handshake = true))
            } finally {
                System.setOut(original)
            }

        process.waitFor()
        assertEquals(0, process.exitValue())
        // 判完不吞输出：标志行转回了本进程终端。
        assertTrue(out.toString(Charsets.UTF_8).contains(HANDSHAKE_LINE), out.toString(Charsets.UTF_8))
    }

    @Test
    fun handshakeOnFailsWhenMarkerMissing() {
        assumeTrue(PlatformDetector.detect() != Platform.WINDOWS, "用 bash -c 做被测进程")

        val thrown =
            assertThrows(IllegalStateException::class.java) {
                processFor(listOf("bash", "-c", "echo nope"), settings(handshake = true))
            }

        assertTrue(thrown.message!!.contains(HANDSHAKE_LINE), thrown.message!!)
    }

    @Test
    fun launchSettingsEnvReachesChildProcess() {
        assumeTrue(PlatformDetector.detect() != Platform.WINDOWS, "用 bash -c 做被测进程")

        val process =
            processFor(
                listOf("bash", "-c", "test \"\$KREMA_DEV_URL\" = http://localhost:5173 && echo $HANDSHAKE_LINE"),
                settings(handshake = true).copy(devUrl = "http://localhost:5173"),
            )

        assertEquals(0, process.waitFor())
    }

    private fun assertTrue(
        condition: Boolean,
        message: String,
    ) = org.junit.jupiter.api.Assertions
        .assertTrue(condition, message)
}
