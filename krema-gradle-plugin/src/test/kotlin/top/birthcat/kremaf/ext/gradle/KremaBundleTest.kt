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

/** 打包 task：jpackage 产出的 app-image、便携包 zip 与形态间产物目录分离。 */
class KremaBundleTest : KremaTestBase() {
    @Test
    @DisplayName("便携包 app-image 自带可执行运行时")
    fun distBundleShipsRuntime(
        @TempDir projectDir: File,
    ) {
        val fakeNpm = writeBundleProject(projectDir)

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_DIST, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DIST}")?.outcome, result.output)
        val image = appImageDir(projectDir, "dist")
        val java = image.resolve("lib/runtime/bin/java" + exeSuffix())
        assertTrue(java.toFile().canExecute(), "app-image 里没有可执行的运行时 java：\n${result.output}")
        assertTrue(
            image.resolve("bin").toFile().isDirectory,
            "app-image 里没有启动器目录：\n${result.output}",
        )
        // 启动器按manifest 里的 Main-Class 找主类；对不上时 jpackage 照样成功，产物却起不来。
        assertEquals(
            MAIN_CLASS,
            jarManifest(appJar(projectDir, "dist"))["Main-Class"],
            "app jar 的 Main-Class 应当与主类产出对得上：\n${result.output}",
        )
    }

    @Test
    @DisplayName("便携包 zip 装的是那份 app-image")
    fun distBundleZipsAppImage(
        @TempDir projectDir: File,
    ) {
        val fakeNpm = writeBundleProject(projectDir)

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_DIST, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DIST}")?.outcome, result.output)
        val zip = portableZip(projectDir, "dist")
        assertTrue(zip.toFile().isFile, "便携包没落 $zip：\n${result.output}")
        val entries = zipEntries(zip)
        assertTrue(
            entries.any { it.startsWith("$APP_IMAGE_DIR_NAME/lib/runtime/bin/") },
            "zip 里没有 app-image 的运行时：\n$entries",
        )
        assertTrue(
            entries.any { it == "$APP_IMAGE_DIR_NAME/lib/app/$APP_NAME.jar" },
            "zip 里没有 app-image 的 app jar：\n$entries",
        )
        // zip 落在被打的目录里，不排掉产物自身就会把它收进去，解开多出一个空壳同名文件。
        assertTrue(
            entries.none { it.endsWith(".zip") },
            "zip 不该把自己收进去：\n$entries",
        )
    }

    @Test
    @DisplayName("前端资产在独立的 assets jar，不在 app jar")
    fun frontendLivesInAssetsJar(
        @TempDir projectDir: File,
    ) {
        val fakeNpm = writeBundleProject(projectDir)

        val result = runGradleWithFakeNpm(projectDir, KremaTaskNames.BUNDLE_DIST, fakeNpm = fakeNpm)

        assertEquals(TaskOutcome.SUCCESS, result.task(":${KremaTaskNames.BUNDLE_DIST}")?.outcome, result.output)
        val appEntries = jarEntries(appJar(projectDir, "dist"))
        assertTrue(
            appEntries.none { it.startsWith("assets/") },
            "前端资产不应合并进 app jar，应独立成 assets jar：\n$appEntries",
        )
        val assetsEntries = jarEntries(assetsJar(projectDir, "dist"))
        assertTrue(
            assetsEntries.contains("assets/index.html"),
            "assets jar 该带上前端资产：\n$assetsEntries",
        )
    }

    @Test
    @DisplayName("两种形态的产物目录分离")
    fun bundleDirsSplitByForm(
        @TempDir projectDir: File,
    ) {
        val fakeNpm = writeBundleProject(projectDir)

        val result =
            runGradleWithFakeNpm(
                projectDir,
                KremaTaskNames.BUNDLE_DIST,
                KremaTaskNames.BUNDLE_PLATFORM,
                fakeNpm = fakeNpm,
            )

        BUNDLE_TASKS.forEach { name ->
            assertEquals(TaskOutcome.SUCCESS, result.task(":$name")?.outcome, "$name 没成功：\n${result.output}")
        }
        listOf("dist", "platform").forEach { form ->
            assertTrue(
                appImageDir(projectDir, form).toFile().isDirectory,
                "$form 下没有 app-image：\n${result.output}",
            )
        }
    }
}
