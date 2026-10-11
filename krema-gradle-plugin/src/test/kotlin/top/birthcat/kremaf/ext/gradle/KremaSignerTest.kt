/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

/** 签名 task：生成 Ed25519 密钥对、用私钥给产物签名。 */
class KremaSignerTest : KremaTestBase() {
    @Test
    @DisplayName("kremaSignerGenerate 生成 Ed25519 密钥对")
    fun signerGenerateKeyPair(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )
        writeToml(projectDir)

        val result = runGradle(projectDir, KremaTaskNames.SIGNER_GENERATE)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.SIGNER_GENERATE}")?.outcome, result.output)
        val key = projectDir.toPath().resolve("build/krema/keys/krema.key")
        assertTrue(key.toFile().isFile, "kremaSignerGenerate 没落私钥")
        assertTrue(
            Files.readString(key).startsWith("-----BEGIN PRIVATE KEY-----"),
            "私钥不是 PEM 形状：\n${Files.readString(key)}",
        )
    }
}
