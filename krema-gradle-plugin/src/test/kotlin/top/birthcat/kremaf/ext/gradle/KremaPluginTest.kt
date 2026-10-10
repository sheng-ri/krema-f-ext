/*
 * Copyright (c) 2026 BirthCat
 * SPDX-License-Identifier: Apache 2.0
 */
package top.birthcat.kremaf.ext.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

class KremaPluginTest {
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
    @DisplayName("krema 分组声明八张 task")
    fun declaresEightKremaTasks(
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
        listOf("[window]", "description", "assets_path", "updater", "permissions").forEach { key ->
            assertTrue(!toml.contains(key), "kremaInit 不该写没有消费者的键 $key：\n$toml")
        }
    }

    @Test
    @DisplayName("默认配置缺失不报错")
    fun defaultConfigAbsentOk(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm =
            fakeNpm(
                projectDir,
                "mkdir -p build/krema/dist\nprintf '<html></html>' > build/krema/dist/index.html",
            )
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
                .resolve("build/krema/dist/index.html")
                .toFile()
                .isFile,
            "前端没产出 dist：\n${result.output}",
        )
    }

    @Test
    @DisplayName("二次构建前端 dist 命中 UP-TO-DATE")
    fun frontendDistUpToDate(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm =
            fakeNpm(
                projectDir,
                "mkdir -p build/krema/dist\nprintf '<html></html>' > build/krema/dist/index.html",
            )
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))

        val first = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUILD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, first.task(":${KremaTaskNames.BUILD}")?.outcome, first.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("build/krema/dist/index.html")
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
    @DisplayName("显式指定缺失配置硬失败")
    fun explicitConfigFileMissingFails(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, "mkdir -p build/krema/dist\necho hi > build/krema/dist/index.html")
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(
            projectDir.toPath().resolve("build.gradle.kts"),
            """
            plugins { id("top.birthcat.kremaf.ext") }
            krema {
                configFile = layout.projectDirectory.file("nope.toml")
                frontend { buildCommand = "${fakeNpm.absolutePath} run build" }
            }
            """.trimIndent(),
        )

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_PROD, fakeNpm = fakeNpm, expectFailure = true)

        assertTrue(
            result.output.contains("nope.toml"),
            "显式指定的 krema.toml 不存在应当指到那个路径：\n${result.output}",
        )
    }

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

    @Test
    @DisplayName("kremaBundleProd 落到 prod 输出目录")
    fun bundleProdOutputDir(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, "mkdir -p build/krema/dist\necho hi > build/krema/dist/index.html")
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))
        writeToml(projectDir)

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_PROD, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_PROD}")?.outcome, result.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("build/krema/prod")
                .toFile()
                .exists(),
            "kremaBundleProd 没落 prod 产物目录",
        )
    }

    @Test
    @DisplayName("dev 与 prod 档产物目录分离")
    fun bundleDevProdSeparateDirs(
        @TempDir projectDir: File,
    ) {
        writeSettings(projectDir)
        val fakeNpm = fakeNpm(projectDir, "mkdir -p build/krema/dist\necho hi > build/krema/dist/index.html")
        Files.writeString(projectDir.toPath().resolve("package.json"), """{ "name": "consumer" }""")
        Files.writeString(projectDir.toPath().resolve("build.gradle.kts"), frontendBuildGradle(fakeNpm.absolutePath))
        writeToml(projectDir)

        val result =
            runGradleWithFakeNpm(
                projectDir,
                KremaTaskNames.BUNDLE_DEV,
                KremaTaskNames.BUNDLE_PROD,
                fakeNpm = fakeNpm,
            )

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DEV}")?.outcome, result.output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_PROD}")?.outcome, result.output)
        assertTrue(
            projectDir
                .toPath()
                .resolve("build/krema/dev")
                .toFile()
                .exists(),
            "dev 档没落 build/krema/dev",
        )
        assertTrue(
            projectDir
                .toPath()
                .resolve("build/krema/prod")
                .toFile()
                .exists(),
            "prod 档没落 build/krema/prod",
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

    /**
     * 与 [runGradle] 同款，但可选地用一个假 `npm` 覆盖 PATH。
     *
     * 唯一接缝就是 TestKit：生产代码不为可测性留任何执行口，所以测试自己写一个假可执行文件，
     * 前置到 PATH 让插件的 `ExecOperations` 找到它（[withEnvironment] 会整体替换环境，
     * 故只保留 PATH，并把原 PATH 接在后面）。长驻 daemon 会缓存进程环境，调用方同时把
     * `buildCommand` 指向假 npm 的绝对路径，双保险。不传 [fakeNpm] 就不改环境。
     */
    private fun runGradleWithFakeNpm(
        projectDir: File,
        vararg args: String,
        fakeNpm: File? = null,
        expectFailure: Boolean = false,
    ) = GradleRunner
        .create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments("--configuration-cache", "--stacktrace", *args)
        .let { runner ->
            val configured =
                if (fakeNpm != null) {
                    runner.withEnvironment(
                        mapOf("PATH" to "${fakeNpm.parentFile.absolutePath}${File.pathSeparator}${System.getenv("PATH")}"),
                    )
                } else {
                    runner
                }
            if (expectFailure) configured.buildAndFail() else configured.build()
        }

    /** 在项目里写一个可执行假 `npm`，脚本内容由调用方给；返回该可执行文件本身。 */
    private fun fakeNpm(
        projectDir: File,
        script: String,
    ): File {
        val dir = projectDir.toPath().resolve("fakebin").toFile()
        dir.mkdirs()
        val npm = dir.toPath().resolve("npm").toFile()
        Files.writeString(npm.toPath(), "#!/bin/sh\n$script\n")
        npm.setExecutable(true)
        return npm
    }

    /** 写一份 `build.gradle.kts`，前端生产命令直接指向假 npm 的绝对路径（外加 `run build` 占位参数）。 */
    private fun frontendBuildGradle(npmPath: String) =
        """
        plugins { id("top.birthcat.kremaf.ext") }
        krema {
            frontend {
                buildCommand = "$npmPath run build"
            }
        }
        """.trimIndent()
}
