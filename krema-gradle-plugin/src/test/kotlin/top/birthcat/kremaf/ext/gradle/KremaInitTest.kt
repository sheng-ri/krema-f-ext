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

/** `kremaInit`：生成项目骨架。 */
class KremaInitTest : KremaTestBase() {
    @Test
    @DisplayName("kremaInit 在缺 toml 时生成骨架")
    fun initCreatesSkeletonNoToml(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """
            plugins { id("top.birthcat.kremaf.ext") }

            krema {
                configFile = layout.projectDirectory.file("krema.toml")
                frontend {
                    buildCommand = "npm run build"
                }
            }
            """.trimIndent(),
        )

        val result = runGradle(projectDir, KremaTaskNames.INIT)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.INIT}")?.outcome, result.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("krema.toml")
                .toFile()
                .isFile,
            "kremaInit 没落 krema.toml",
        )
    }

    @Test
    @DisplayName("kremaInit 不写无消费者的键")
    fun initOmitsUnusedKeys(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )

        val result = runGradle(projectDir, KremaTaskNames.INIT)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.INIT}")?.outcome, result.output)
        val toml = Files.readString(projectDir.toPath().resolve("krema.toml"))
        listOf("[window]", "description", "updater", "permissions").forEach { key ->
            assertTrue(!toml.contains(key), "kremaInit 不该写没有消费者的键 $key：\n$toml")
        }
    }
}
