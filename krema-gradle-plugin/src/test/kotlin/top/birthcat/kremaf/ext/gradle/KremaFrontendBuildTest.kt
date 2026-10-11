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

/** `kremaFrontendBuild`：跑前端生产构建并把 `dist` 声明成可缓存输出。 */
class KremaFrontendBuildTest : KremaTestBase() {
    @Test
    @DisplayName("二次构建前端 dist 命中 UP-TO-DATE")
    fun frontendDistUpToDate(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val first = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUILD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, first.task(":${KremaTaskNames.BUILD}")?.outcome, first.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("dist/index.html")
                .toFile()
                .isFile,
            "首次运行没产出 dist：\n${first.output}",
        )

        val second = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUILD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":${KremaTaskNames.BUILD}")?.outcome, second.output)
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":${KremaTaskNames.FRONTEND_BUILD}")?.outcome,
            "前端 task 输入没变时应当 UP-TO-DATE：\n${second.output}",
        )
    }

    @Test
    @DisplayName("前端构建失败透出命令输出")
    fun frontendFailureShowsOutput(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val marker = "FAKE_NPM_DIAGNOSTIC_LINE_abc123"
        val fakeNpm = fakeNpm(projectDir, "echo \"$marker\" >&2\nexit 1")
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val result =
            runGradleWithFakeNpm(
                projectDir,
                KremaTaskNames.BUILD,
                fakeNpm = fakeNpm,
                expectFailure = true,
            )

        assertTrue(
            result.output.contains("前端构建失败"),
            "失败信息应当带上命令输出：\n${result.output}",
        )
        assertTrue(
            result.output.contains(marker),
            "失败信息应当包含命令自身打印的内容：\n${result.output}",
        )
    }

    @Test
    @DisplayName("前端产物目录认 krema.toml 的 out_dir")
    fun frontendHonorsOutDir(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        writeMainJava(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("krema.toml"),
            """
            [package]
            name = "consumer-app"
            identifier = "com.example.app"
            version = "0.1.0"

            [build]
            out_dir = "custom-dist"
            """.trimIndent(),
        )
        val fakeNpm = fakeNpm(projectDir, frontendBuildScript("custom-dist"))
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_DIST, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DIST}")?.outcome, result.output)
        // 前端命令是用户给的，产物落在哪由 out_dir 说了算；插件不认它就会读空目录，assets jar 里没有 assets/。
        val entries = jarEntries(assetsJar(projectDir, "dist"))
        assertTrue(
            entries.contains("assets/index.html"),
            "out_dir 改了却没跟着收进 assets jar：\n$entries",
        )
    }

    @Test
    @DisplayName("前端目录配置后命令在该目录执行")
    fun frontendDirMovesCommandCwd(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        writeFrontendDir(projectDir, fakeNpm.absolutePath, "front")

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.FRONTEND_BUILD, fakeNpm = fakeNpm)

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":${KremaTaskNames.FRONTEND_BUILD}")?.outcome,
            result.output,
        )
        assertTrue(
            projectDir.toPath().resolve("front/dist/index.html").toFile().isFile,
            "前端目录没生效，产物应当落在 front/dist：\n${result.output}",
        )
        assertTrue(
            !projectDir.toPath().resolve("dist/index.html").toFile().exists(),
            "产物不该再落在项目根：\n${result.output}",
        )
    }

    @Test
    @DisplayName("前端目录认 krema.toml 的 frontend_dir")
    fun frontendDirFromToml(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("krema.toml"),
            """
            [package]
            name = "consumer-app"

            [build]
            frontend_dir = "front"
            """.trimIndent(),
        )
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        projectDir.toPath().resolve("front").toFile().mkdirs()
        Files.writeString(projectDir.toPath().resolve("front/package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.FRONTEND_BUILD, fakeNpm = fakeNpm)

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":${KremaTaskNames.FRONTEND_BUILD}")?.outcome,
            result.output,
        )
        assertTrue(
            projectDir.toPath().resolve("front/dist/index.html").toFile().isFile,
            "toml 的 frontend_dir 没生效：\n${result.output}",
        )
    }

    @Test
    @DisplayName("扩展的 dir 压过 toml 的 frontend_dir")
    fun frontendDirExtOverridesToml(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        Files.writeString(
            projectDir.toPath().resolve("krema.toml"),
            """
            [package]
            name = "consumer-app"

            [build]
            frontend_dir = "toml-side"
            """.trimIndent(),
        )
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        writeFrontendDir(projectDir, fakeNpm.absolutePath, "ext-side")

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.FRONTEND_BUILD, fakeNpm = fakeNpm)

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":${KremaTaskNames.FRONTEND_BUILD}")?.outcome,
            result.output,
        )
        assertTrue(
            projectDir.toPath().resolve("ext-side/dist/index.html").toFile().isFile,
            "扩展的 dir 应当压过 toml：\n${result.output}",
        )
        assertTrue(
            !projectDir.toPath().resolve("toml-side").toFile().exists(),
            "toml 那一侧不该留下产物：\n${result.output}",
        )
    }

    @Test
    @DisplayName("前端输入跟着前端目录解析")
    fun frontendInputsFollowDir(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, FRONTEND_BUILD_SCRIPT)
        writeFrontendDir(projectDir, fakeNpm.absolutePath, "front")

        val first = runGradleWithFakeNpm(projectDir, KremaTaskNames.FRONTEND_BUILD, fakeNpm = fakeNpm)

        assertEquals(
            TaskOutcome.SUCCESS,
            first.task(":${KremaTaskNames.FRONTEND_BUILD}")?.outcome,
            first.output,
        )
        val second = runGradleWithFakeNpm(projectDir, KremaTaskNames.FRONTEND_BUILD, fakeNpm = fakeNpm)

        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":${KremaTaskNames.FRONTEND_BUILD}")?.outcome,
            "前端输入没变时应当 UP-TO-DATE：\n${second.output}",
        )
        Files.writeString(
            projectDir.toPath().resolve("front/package.json"),
            """{ "name": "consumer", "version": "2" }""",
        )
        val third = runGradleWithFakeNpm(projectDir, KremaTaskNames.FRONTEND_BUILD, fakeNpm = fakeNpm)

        assertEquals(
            TaskOutcome.SUCCESS,
            third.task(":${KremaTaskNames.FRONTEND_BUILD}")?.outcome,
            "front/package.json 变了却没重跑，说明输入没跟着前端目录走：\n${third.output}",
        )
    }

    /** 摆一份前端目录：`[dir]/package.json`，以及把生产命令指向假 npm 的构建脚本。 */
    private fun writeFrontendDir(
        projectDir: File,
        npmPath: String,
        dir: String,
    ) {
        val front = projectDir.toPath().resolve(dir).toFile()
        front.mkdirs()
        Files.writeString(front.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendDirGradle(npmPath, dir))
    }

    /** [frontendBuildGradle] 的带目录版：目录与命令一起写在扩展块里。 */
    private fun frontendDirGradle(
        npmPath: String,
        dir: String,
    ) = """
        plugins { id("top.birthcat.kremaf.ext") }
        krema {
            frontend {
                dir = "$dir"
                buildCommand = "${kotlinString(npmPath)} run build"
            }
        }
        """.trimIndent()
}
