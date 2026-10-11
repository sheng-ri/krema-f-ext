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

/** 插件应用、扩展注册与 task 声明：这些是使用方最先碰到的那一层。 */
class KremaExtensionTest : KremaTestBase() {
    @Test
    @DisplayName("插件应用后注册 krema 扩展")
    fun pluginRegistersExtension(
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
    @DisplayName("krema 分组列出两种形态的打包 task")
    fun declaresKremaTasks(
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
        assertEquals(
            BUNDLE_TASKS.size,
            BUNDLE_TASKS.count { result.output.contains(it) },
            "打包 task 应当正好是两种形态：\n${result.output}",
        )
    }

    @Test
    @DisplayName("默认配置缺失不报错")
    fun defaultConfigAbsentOk(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        // 没有 krema.toml 是这条用例的要点；但 kremaBuild 现在也会跑前端，所以给一份最小前端输入。
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUILD, fakeNpm = fakeNpm)

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":${KremaTaskNames.BUILD}")?.outcome,
            "默认路径下没有 krema.toml 不应报错：\n${result.output}",
        )
        assertTrue(
            projectDir
                .toPath()
                .resolve("dist/index.html")
                .toFile()
                .isFile,
            "前端没产出 dist：\n${result.output}",
        )
    }

    @Test
    @DisplayName("显式指定缺失配置硬失败")
    fun explicitConfigFileMissingFails(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        // 打包 task 经 kremaRuntimeImage 连到 jdeps：没有编译产出时 jdeps 先于配置校验失败，
        // 报错就指不到 krema.toml 了，故给一份最小主类让流水线走到配置读取那一步。
        writeMainJava(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """
            plugins { id("top.birthcat.kremaf.ext") }
            krema {
                configFile = layout.projectDirectory.file("nope.toml")
                frontend { buildCommand = "${kotlinString(fakeNpm.absolutePath)} run build" }
            }
            """.trimIndent(),
        )

        val result =
            runGradleWithFakeNpm(
                projectDir,
                KremaTaskNames.BUNDLE_DIST,
                fakeNpm = fakeNpm,
                expectFailure = true,
            )

        assertTrue(
            result.output.contains("nope.toml"),
            "显式指定的 krema.toml 不存在应当指到那个路径：\n${result.output}",
        )
    }
}
