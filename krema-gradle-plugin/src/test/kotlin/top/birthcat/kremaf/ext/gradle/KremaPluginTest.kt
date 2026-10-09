/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

class KremaPluginTest {
    @Test
    fun appliesPluginAndRegistersExtension(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """
            plugins { id("top.birthcat.kremaf.ext") }

            tasks.register("showExt") {
                val ext = project.extensions.getByName("krema")
                doLast { println("ext=" + ext.javaClass.name) }
            }
            """.trimIndent(),
        )

        val result = runGradle(projectDir, "showExt")

        assertTrue(
            result.output.contains("ext=top.birthcat.kremaf.ext.gradle.KremaExtension"),
            result.output,
        )
    }

    @Test
    fun declaresAllSevenTasksInKremaGroup(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )

        val result = runGradle(projectDir, "tasks", "--group", KremaTaskNames.GROUP)

        KremaTaskNames.ALL.forEach { name ->
            assertTrue(result.output.contains(name), "task $name 未出现在 krema 分组里：\n${result.output}")
        }
    }

    @Test
    fun kremaInitGeneratesSkeletonWithoutExistingToml(
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
                    devCommand = "npm run dev"
                    devUrl = "http://localhost:5173"
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
    fun kremaInitWritesNoConfigKeyWithoutConsumer(
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
        listOf("[window]", "description", "assets_path", "updater", "permissions").forEach { key ->
            assertTrue(!toml.contains(key), "kremaInit 不该写没有消费者的键 $key：\n$toml")
        }
    }

    @Test
    fun buildFailsLoudlyWithoutToml(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )

        val result = runGradle(projectDir, KremaTaskNames.BUILD, expectFailure = true)

        assertTrue(
            result.output.contains("找不到 krema.toml"),
            "krema.toml 缺失时应当硬报错：\n${result.output}",
        )
    }

    @Test
    fun signerGenerateProducesEd25519KeyPair(
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

    @Test
    fun bundleRunsRealOrchestrationPath(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """plugins { id("top.birthcat.kremaf.ext") }""",
        )
        writeToml(projectDir)

        val result = runGradle(projectDir, KremaTaskNames.BUNDLE)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE}")?.outcome, result.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("build/krema")
                .toFile()
                .exists(),
            "kremaBundle 没落产物目录",
        )
    }

    private fun writeToml(projectDir: File) {
        Files.writeString(
            projectDir.toPath().resolve("krema.toml"),
            """
            [package]
            name = "consumer-app"
            identifier = "com.example.consumer"
            version = "0.1.0"
            """.trimIndent(),
        )
    }

    private fun writeSettings(projectDir: File) {
        Files.writeString(
            projectDir.toPath().resolve("settings.gradle.kts"),
            "rootProject.name = \"consumer\"\n",
        )
    }

    private fun runGradle(
        projectDir: File,
        vararg args: String,
        expectFailure: Boolean = false,
    ) = GradleRunner
        .create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*args, "--configuration-cache", "--stacktrace")
        .let { if (expectFailure) it.buildAndFail() else it.build() }
}
